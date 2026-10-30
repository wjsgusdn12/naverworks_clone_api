package com.jhwjhw.naverworkscloneapi.monitoring;

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

// 5분 칸을 DB에 "더하기"로 기록하고 읽음. 칸 자리는 300개를 돌려 씀(25시간분).
// 같은 자리에 옛 칸(25시간 전)이 남아 있으면 더하지 않고 새 값으로 덮어씀 → 지우는 쿼리 없이 행 수가 고정됨.
// 서버 1·2가 같은 행에 동시에 써도 숫자가 맞도록 1시간 요약과 같은 방식:
//   1) UPDATE (같은 칸이면 더하기, 옛 칸이면 덮어쓰기)  2) 바뀐 행이 없으면 INSERT  3) 중복 오류면 다시 1)
@Repository
@RequiredArgsConstructor
public class ApiMetricBucketRepository {
    // 돌려 쓰는 자리 수. 25시간 = 300칸(대시보드는 최근 24시간만 봄).
    static final int SLOTS = 300;
    private static final String TOTAL = ApiMetricHourlyRepository.TOTAL;
    private static final int ENDPOINT_MAX = 300;
    private static final int LATENCY_COLUMNS = MetricsBucket.LATENCY_BOUNDS_MS.length + 1;
    private static final List<String> VALUE_COLUMNS = new ArrayList<>(List.of("request_count", "client_errors",
            "server_errors", "duration_sum_ms"));
    static {
        IntStream.range(0, LATENCY_COLUMNS).forEach(i -> VALUE_COLUMNS.add("latency_" + i));
    }
    // 칸 번호가 같으면 기존 값 + 새 값, 다르면(옛 칸) 새 값으로 덮어씀.
    private static final String UPDATE_SQL = "UPDATE api_metric_bucket SET "
            + VALUE_COLUMNS.stream().map(column -> column + " = CASE WHEN bucket = ? THEN " + column + " + ? ELSE ? END")
                    .collect(Collectors.joining(", "))
            // 늦게 도착한 옛 칸이 더 새로운 칸을 덮어쓰지 않도록 bucket <= 새 칸 번호일 때만.
            + ", bucket = ? WHERE slot = ? AND endpoint = ? AND bucket <= ?";
    private static final String INSERT_SQL = "INSERT INTO api_metric_bucket (slot, endpoint, bucket, "
            + String.join(", ", VALUE_COLUMNS) + ") VALUES (?, ?, ?, "
            + VALUE_COLUMNS.stream().map(column -> "?").collect(Collectors.joining(", ")) + ")";

    private final JdbcTemplate jdbc;

    // 한 칸을 더함: 전체 합계 행(응답 시간 구간 포함) + API별 행(구간 칸은 0)
    public void addBucket(long bucket, MetricsBucket data) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(TOTAL, values(data.getCount(), data.getClientErrors(), data.getServerErrors(),
                data.getDurationSumMs(), data.getLatencyCounts())));
        data.getEndpoints().forEach((endpoint, stats) -> rows.add(new Row(endpoint, values(stats.getCount(),
                stats.getClientErrors(), stats.getServerErrors(), stats.getDurationSumMs(), new long[LATENCY_COLUMNS]))));
        int slot = (int) Math.floorMod(bucket, (long) SLOTS);
        // UPDATE를 한 번에 묶어 보내고(batch), 바뀐 행이 없던 것만 새로 만듦.
        int[] updated = jdbc.batchUpdate(UPDATE_SQL, rows.stream().map(row -> updateArgs(bucket, slot, row)).toList());
        for (int i = 0; i < rows.size(); i++) {
            if (updated[i] != 0) continue;
            Row row = rows.get(i);
            try {
                jdbc.update(INSERT_SQL, insertArgs(bucket, slot, row));
            } catch (DuplicateKeyException exception) {
                // 다른 서버가 방금 같은 행을 만들었으면 거기에 더함.
                jdbc.update(UPDATE_SQL, updateArgs(bucket, slot, row));
            }
        }
    }

    // fromBucket~toBucket 범위의 칸을 읽음. 기록이 없는 칸은 빠짐.
    public Map<Long, MetricsBucket> read(long fromBucket, long toBucket) {
        Map<Long, MetricsBucket> result = new HashMap<>();
        jdbc.query("SELECT bucket, endpoint, " + String.join(", ", VALUE_COLUMNS)
                + " FROM api_metric_bucket WHERE bucket BETWEEN ? AND ?", resultSet -> {
                    MetricsBucket bucket = result.computeIfAbsent(resultSet.getLong("bucket"), key -> new MetricsBucket());
                    String endpoint = resultSet.getString("endpoint");
                    long count = resultSet.getLong("request_count");
                    long clientErrors = resultSet.getLong("client_errors");
                    long serverErrors = resultSet.getLong("server_errors");
                    long durationSum = resultSet.getLong("duration_sum_ms");
                    if (TOTAL.equals(endpoint)) {
                        bucket.addTotals(count, clientErrors, serverErrors, durationSum);
                        for (int i = 0; i < LATENCY_COLUMNS; i++) bucket.addLatency(i, resultSet.getLong("latency_" + i));
                    } else {
                        bucket.endpoint(endpoint).add(count, clientErrors, serverErrors, durationSum);
                    }
                }, fromBucket, toBucket);
        return result;
    }

    private record Row(String endpoint, long[] values) {
    }

    private static long[] values(long count, long clientErrors, long serverErrors, long durationSum, long[] latency) {
        long[] values = new long[4 + latency.length];
        values[0] = count;
        values[1] = clientErrors;
        values[2] = serverErrors;
        values[3] = durationSum;
        System.arraycopy(latency, 0, values, 4, latency.length);
        return values;
    }

    private static String endpointOf(Row row) {
        return row.endpoint().length() > ENDPOINT_MAX ? row.endpoint().substring(0, ENDPOINT_MAX) : row.endpoint();
    }

    // 열마다 (칸 번호, 더할 값, 덮어쓸 값) + 새 칸 번호, 자리, 엔드포인트, 칸 번호(옛 칸 확인용)
    private static Object[] updateArgs(long bucket, int slot, Row row) {
        List<Object> args = new ArrayList<>();
        for (long value : row.values()) {
            args.add(bucket);
            args.add(value);
            args.add(value);
        }
        args.add(bucket);
        args.add(slot);
        args.add(endpointOf(row));
        args.add(bucket);
        return args.toArray();
    }

    private static Object[] insertArgs(long bucket, int slot, Row row) {
        List<Object> args = new ArrayList<>(List.of(slot, endpointOf(row), bucket));
        for (long value : row.values()) args.add(value);
        return args.toArray();
    }
}
