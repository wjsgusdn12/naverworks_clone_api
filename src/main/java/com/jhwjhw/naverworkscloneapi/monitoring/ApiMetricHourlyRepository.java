package com.jhwjhw.naverworkscloneapi.monitoring;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// 1시간 요약을 DB에 "더하기"로 기록하고 읽음.
// 한 시간마다 전체 합계 행('*') 1개 + API별 행 여러 개를 씀.
// 서버 1·2가 같은 시간·같은 API 행에 동시에 써도 숫자가 맞도록:
//   1) UPDATE ... SET 값 = 값 + 새값  (DB가 한 행씩 잠그고 더하므로 안전)
//   2) 바뀐 행이 없으면(처음 기록) INSERT
//   3) 그 사이 다른 서버가 먼저 INSERT해 "중복" 오류가 나면 다시 1)로 더함
@Repository
@RequiredArgsConstructor
public class ApiMetricHourlyRepository {
    // 전체 합계 행의 endpoint 값
    public static final String TOTAL = "*";
    // endpoint 칸 길이(V27의 VARCHAR(300))
    private static final int ENDPOINT_MAX = 300;
    private static final int LATENCY_COLUMNS = MetricsBucket.LATENCY_BOUNDS_MS.length + 1;
    private static final String LATENCY_UPDATE = IntStream.range(0, LATENCY_COLUMNS)
            .mapToObj(i -> "latency_" + i + " = latency_" + i + " + ?").collect(Collectors.joining(", "));
    private static final String LATENCY_NAMES = IntStream.range(0, LATENCY_COLUMNS)
            .mapToObj(i -> "latency_" + i).collect(Collectors.joining(", "));
    private static final String UPDATE_SQL = "UPDATE api_metric_hourly SET request_count = request_count + ?, "
            + "client_errors = client_errors + ?, server_errors = server_errors + ?, "
            + "duration_sum_ms = duration_sum_ms + ?, " + LATENCY_UPDATE + " WHERE hour_start = ? AND endpoint = ?";
    private static final String INSERT_SQL = "INSERT INTO api_metric_hourly (request_count, client_errors, "
            + "server_errors, duration_sum_ms, " + LATENCY_NAMES + ", hour_start, endpoint) VALUES ("
            + "?, ".repeat(4 + LATENCY_COLUMNS) + "?, ?)";

    private final JdbcTemplate jdbc;

    // API 한 개의 한 시간 기록(작은 추이선용)
    public record EndpointHour(Instant hourStart, String endpoint, long requests, long durationSumMs) {
    }

    // 한 시간치를 더함: 전체 합계 행(응답 시간 구간 포함) + API별 행(구간 칸은 0)
    public void addHour(Instant hourStart, MetricsBucket bucket) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(values(hourStart, TOTAL, bucket.getCount(), bucket.getClientErrors(), bucket.getServerErrors(),
                bucket.getDurationSumMs(), bucket.getLatencyCounts()));
        bucket.getEndpoints().forEach((endpoint, stats) -> rows.add(values(hourStart, endpoint, stats.getCount(),
                stats.getClientErrors(), stats.getServerErrors(), stats.getDurationSumMs(), new long[LATENCY_COLUMNS])));
        // UPDATE를 한 번에 묶어 보내고(batch), 바뀐 행이 없던 것만 새로 만듦.
        int[] updated = jdbc.batchUpdate(UPDATE_SQL, rows);
        for (int i = 0; i < rows.size(); i++) {
            if (updated[i] != 0) continue;
            try {
                jdbc.update(INSERT_SQL, rows.get(i));
            } catch (DuplicateKeyException exception) {
                // 다른 서버가 방금 같은 행을 만들었으면 거기에 더함.
                jdbc.update(UPDATE_SQL, rows.get(i));
            }
        }
    }

    private static Object[] values(Instant hourStart, String endpoint, long count, long clientErrors,
            long serverErrors, long durationSum, long[] latency) {
        List<Object> values = new ArrayList<>(List.of(count, clientErrors, serverErrors, durationSum));
        for (long value : latency) values.add(value);
        values.add(Timestamp.from(hourStart));
        values.add(endpoint.length() > ENDPOINT_MAX ? endpoint.substring(0, ENDPOINT_MAX) : endpoint);
        return values.toArray();
    }

    // from 이후의 전체 합계 행을 시간별 집계 칸으로 읽음.
    public Map<Instant, MetricsBucket> readTotals(Instant from) {
        Map<Instant, MetricsBucket> result = new HashMap<>();
        jdbc.query("SELECT hour_start, request_count, client_errors, server_errors, duration_sum_ms, " + LATENCY_NAMES
                + " FROM api_metric_hourly WHERE endpoint = ? AND hour_start >= ?", resultSet -> {
                    MetricsBucket bucket = new MetricsBucket();
                    bucket.addTotals(resultSet.getLong("request_count"), resultSet.getLong("client_errors"),
                            resultSet.getLong("server_errors"), resultSet.getLong("duration_sum_ms"));
                    for (int i = 0; i < LATENCY_COLUMNS; i++) bucket.addLatency(i, resultSet.getLong("latency_" + i));
                    result.put(resultSet.getTimestamp("hour_start").toInstant(), bucket);
                }, TOTAL, Timestamp.from(from));
        return result;
    }

    // [from, to) 기간의 API별 합계(순위용). DB가 API별로 더해서 돌려주므로 행이 많아도 결과는 API 수만큼.
    public Map<String, MetricsBucket.EndpointStats> readEndpointTotals(Instant from, Instant to) {
        Map<String, MetricsBucket.EndpointStats> result = new HashMap<>();
        jdbc.query("SELECT endpoint, SUM(request_count) AS requests, SUM(client_errors) AS client_errors, "
                + "SUM(server_errors) AS server_errors, SUM(duration_sum_ms) AS duration_sum FROM api_metric_hourly "
                + "WHERE endpoint <> ? AND hour_start >= ? AND hour_start < ? GROUP BY endpoint", resultSet -> {
                    MetricsBucket.EndpointStats stats = new MetricsBucket.EndpointStats();
                    stats.add(resultSet.getLong("requests"), resultSet.getLong("client_errors"),
                            resultSet.getLong("server_errors"), resultSet.getLong("duration_sum"));
                    result.put(resultSet.getString("endpoint"), stats);
                }, TOTAL, Timestamp.from(from), Timestamp.from(to));
        return result;
    }

    // [from, to) 기간에서 고른 API들의 시간별 기록(순위 옆 작은 추이선용).
    public List<EndpointHour> readEndpointHours(Instant from, Instant to, Collection<String> endpoints) {
        if (endpoints.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(from), Timestamp.from(to)));
        args.addAll(endpoints);
        String placeholders = endpoints.stream().map(endpoint -> "?").collect(Collectors.joining(", "));
        return jdbc.query("SELECT hour_start, endpoint, request_count, duration_sum_ms FROM api_metric_hourly "
                + "WHERE hour_start >= ? AND hour_start < ? AND endpoint IN (" + placeholders + ")",
                (resultSet, rowNumber) -> new EndpointHour(resultSet.getTimestamp("hour_start").toInstant(),
                        resultSet.getString("endpoint"), resultSet.getLong("request_count"),
                        resultSet.getLong("duration_sum_ms")),
                args.toArray());
    }
}
