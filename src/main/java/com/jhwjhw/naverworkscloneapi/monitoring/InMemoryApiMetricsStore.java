package com.jhwjhw.naverworkscloneapi.monitoring;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 메모리 버전 집계판(로컬 개발용). 서버가 한 대뿐이라 합산할 필요가 없고, 재시작하면 0부터 다시 쌓임.
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryApiMetricsStore implements ApiMetricsStore {
    private final Map<Long, MetricsBucket> buckets = new HashMap<>();
    private final Map<String, ServerStatus> servers = new ConcurrentHashMap<>();

    @Override
    public synchronized void merge(Map<Long, MetricsBucket> deltas) {
        deltas.forEach((bucket, delta) -> buckets.computeIfAbsent(bucket, key -> new MetricsBucket()).add(delta));
        // 보관 기간이 지난 칸 정리
        long oldest = ApiMetricsRecorder.bucketOf(Instant.now().minus(RETENTION));
        buckets.keySet().removeIf(bucket -> bucket < oldest);
    }

    @Override
    public synchronized Map<Long, MetricsBucket> read(long fromBucket, long toBucket) {
        Map<Long, MetricsBucket> result = new HashMap<>();
        buckets.forEach((bucket, data) -> {
            if (bucket < fromBucket || bucket > toBucket) return;
            // 바깥에서 고쳐도 원본이 바뀌지 않도록 복사본을 돌려줌
            MetricsBucket copy = new MetricsBucket();
            copy.add(data);
            result.put(bucket, copy);
        });
        return result;
    }

    @Override
    public void reportServer(ServerStatus status) {
        servers.put(status.instanceId(), status);
    }

    @Override
    public List<ServerStatus> servers(Instant now) {
        servers.values().removeIf(status -> status.reportedAt().isBefore(now.minus(SERVER_TIMEOUT)));
        return servers.values().stream().sorted(Comparator.comparing(ServerStatus::instanceId)).toList();
    }

    @Override
    public String mode() {
        return "LOCAL";
    }
}
