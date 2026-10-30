package com.jhwjhw.naverworkscloneapi.monitoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// Redis 버전 집계판(배포용). 서버 1·2가 같은 키에 더하기(HINCRBY)만 하므로 동시에 써도 합계가 틀리지 않음
// (Redis는 명령을 하나씩 차례로 처리함). 어느 서버에서 읽어도 두 서버를 합친 같은 값이 나옴.
//
// 저장 모양
// - apiMetrics:bucket:{5분 칸 번호}  해시. count·e4·e5·sum·h0~h8(응답 시간 구간), ep|{엔드포인트}|c·e4·e5·s
// - apiMetrics:servers               해시. 서버 이름 → "시작시각|보고시각|사용메모리|최대메모리|실시간연결수"
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RedisApiMetricsStore implements ApiMetricsStore {
    private static final String BUCKET_KEY = "apiMetrics:bucket:";
    private static final String SERVERS_KEY = "apiMetrics:servers";

    private final StringRedisTemplate redis;

    @Override
    public void merge(Map<Long, MetricsBucket> deltas) {
        if (deltas.isEmpty()) return;
        // 여러 명령을 한 번에 묶어 보내(파이프라인) 왕복 횟수를 줄임.
        redis.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                deltas.forEach((bucket, delta) -> {
                    String key = BUCKET_KEY + bucket;
                    increment(ops, key, "count", delta.getCount());
                    increment(ops, key, "e4", delta.getClientErrors());
                    increment(ops, key, "e5", delta.getServerErrors());
                    increment(ops, key, "sum", delta.getDurationSumMs());
                    long[] latency = delta.getLatencyCounts();
                    for (int i = 0; i < latency.length; i++) increment(ops, key, "h" + i, latency[i]);
                    delta.getEndpoints().forEach((endpoint, stats) -> {
                        increment(ops, key, "ep|" + endpoint + "|c", stats.getCount());
                        increment(ops, key, "ep|" + endpoint + "|e4", stats.getClientErrors());
                        increment(ops, key, "ep|" + endpoint + "|e5", stats.getServerErrors());
                        increment(ops, key, "ep|" + endpoint + "|s", stats.getDurationSumMs());
                    });
                    // 쓸 때마다 보관 기간을 다시 걸어, 오래된 칸은 Redis가 알아서 지움.
                    ops.expire(key, RETENTION);
                });
                return null;
            }
        });
    }

    private static void increment(RedisOperations<String, String> ops, String key, String field, long value) {
        if (value != 0) ops.opsForHash().increment(key, field, value);
    }

    @Override
    public Map<Long, MetricsBucket> read(long fromBucket, long toBucket) {
        List<Long> bucketIds = new ArrayList<>();
        for (long bucket = fromBucket; bucket <= toBucket; bucket++) bucketIds.add(bucket);
        // 24시간 = 288칸을 한 번에 읽음.
        List<Object> results = redis.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                bucketIds.forEach(bucket -> ops.opsForHash().entries(BUCKET_KEY + bucket));
                return null;
            }
        });
        Map<Long, MetricsBucket> buckets = new HashMap<>();
        for (int i = 0; i < bucketIds.size(); i++) {
            if (!(results.get(i) instanceof Map<?, ?> fields) || fields.isEmpty()) continue;
            buckets.put(bucketIds.get(i), toBucket(fields));
        }
        return buckets;
    }

    // Redis 해시 내용을 집계 칸으로 되돌림.
    private static MetricsBucket toBucket(Map<?, ?> fields) {
        MetricsBucket bucket = new MetricsBucket();
        bucket.addTotals(number(fields.get("count")), number(fields.get("e4")), number(fields.get("e5")),
                number(fields.get("sum")));
        for (int i = 0; i < bucket.getLatencyCounts().length; i++) bucket.addLatency(i, number(fields.get("h" + i)));
        fields.forEach((rawField, rawValue) -> {
            String field = String.valueOf(rawField);
            if (!field.startsWith("ep|")) return;
            int last = field.lastIndexOf('|');
            String endpoint = field.substring(3, last);
            long value = number(rawValue);
            switch (field.substring(last + 1)) {
                case "c" -> bucket.endpoint(endpoint).add(value, 0, 0, 0);
                case "e4" -> bucket.endpoint(endpoint).add(0, value, 0, 0);
                case "e5" -> bucket.endpoint(endpoint).add(0, 0, value, 0);
                case "s" -> bucket.endpoint(endpoint).add(0, 0, 0, value);
                default -> {
                }
            }
        });
        return bucket;
    }

    private static long number(Object value) {
        return value == null ? 0 : Long.parseLong(String.valueOf(value));
    }

    @Override
    public void reportServer(ServerStatus status) {
        redis.opsForHash().put(SERVERS_KEY, status.instanceId(),
                status.startedAt().toEpochMilli() + "|" + status.reportedAt().toEpochMilli() + "|"
                        + status.heapUsedMb() + "|" + status.heapMaxMb() + "|" + status.realtimeConnections());
    }

    @Override
    public List<ServerStatus> servers(Instant now) {
        List<ServerStatus> alive = new ArrayList<>();
        redis.opsForHash().entries(SERVERS_KEY).forEach((rawId, rawValue) -> {
            String[] parts = String.valueOf(rawValue).split("\\|");
            if (parts.length != 5) return;
            ServerStatus status = new ServerStatus(String.valueOf(rawId), Instant.ofEpochMilli(Long.parseLong(parts[0])),
                    Instant.ofEpochMilli(Long.parseLong(parts[1])), Long.parseLong(parts[2]), Long.parseLong(parts[3]),
                    Integer.parseInt(parts[4]));
            if (status.reportedAt().isBefore(now.minus(SERVER_TIMEOUT))) {
                // 30초 넘게 보고가 없으면 꺼진 서버로 보고 목록에서 지움.
                redis.opsForHash().delete(SERVERS_KEY, rawId);
            } else {
                alive.add(status);
            }
        });
        alive.sort(Comparator.comparing(ServerStatus::instanceId));
        return alive;
    }

    @Override
    public String mode() {
        return "SHARED";
    }
}
