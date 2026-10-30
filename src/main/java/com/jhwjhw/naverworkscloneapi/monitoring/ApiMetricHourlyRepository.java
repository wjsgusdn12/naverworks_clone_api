package com.jhwjhw.naverworkscloneapi.monitoring;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
// 서버 1·2가 같은 시간·같은 API 행에 동시에 써도 숫자가 맞도록:
//   1) UPDATE ... SET 값 = 값 + 새값  (DB가 한 행씩 잠그고 더하므로 안전)
//   2) 바뀐 행이 없으면(처음 기록) INSERT
//   3) 그 사이 다른 서버가 먼저 INSERT해 "중복" 오류가 나면 다시 1)로 더함
@Repository
@RequiredArgsConstructor
public class ApiMetricHourlyRepository {
    // 전체 합계 행의 endpoint 값
    public static final String TOTAL = "*";
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

    // 한 시간치 합계를 더함. 지금은 일주일 그래프에 필요한 전체 합계 행('*')만 기록
    // (API별 일주일 순위가 필요해지면 같은 표에 endpoint별 행을 추가하면 됨).
    public void addHour(Instant hourStart, MetricsBucket bucket) {
        addRow(hourStart, TOTAL, bucket.getCount(), bucket.getClientErrors(), bucket.getServerErrors(),
                bucket.getDurationSumMs(), bucket.getLatencyCounts());
    }

    private void addRow(Instant hourStart, String endpoint, long count, long clientErrors, long serverErrors,
            long durationSum, long[] latency) {
        Object[] values = values(hourStart, endpoint, count, clientErrors, serverErrors, durationSum, latency);
        if (jdbc.update(UPDATE_SQL, values) > 0) return;
        try {
            jdbc.update(INSERT_SQL, values);
        } catch (DuplicateKeyException exception) {
            // 다른 서버가 방금 같은 행을 만들었으면 거기에 더함.
            jdbc.update(UPDATE_SQL, values);
        }
    }

    private static Object[] values(Instant hourStart, String endpoint, long count, long clientErrors,
            long serverErrors, long durationSum, long[] latency) {
        List<Object> values = new ArrayList<>(List.of(count, clientErrors, serverErrors, durationSum));
        for (long value : latency) values.add(value);
        values.add(Timestamp.from(hourStart));
        values.add(endpoint);
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
}
