package com.jhwjhw.naverworkscloneapi.monitoring;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 요청 기록을 이 서버의 작은 상자(pending)에 모았다가 5초마다 집계판(ApiMetricsStore)으로 한꺼번에 보냄.
// 요청마다 Redis에 다녀오면 응답이 느려지므로, 모아서 보내는 방식으로 부담을 줄임.
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiMetricsRecorder {
    // 한 칸의 길이(초). 5분 단위로 모아 24시간이면 288칸.
    public static final long BUCKET_SECONDS = 300;

    private final ApiMetricsStore store;
    private Map<Long, MetricsBucket> pending = new HashMap<>();

    public static long bucketOf(Instant at) {
        return at.getEpochSecond() / BUCKET_SECONDS;
    }

    public synchronized void record(String endpoint, int status, long durationMs, Instant at) {
        pending.computeIfAbsent(bucketOf(at), key -> new MetricsBucket()).record(endpoint, status, durationMs);
    }

    // 모아 둔 기록을 집계판에 보냄. 대시보드를 열 때도 먼저 불러 최신 기록이 빠지지 않게 함.
    @Scheduled(fixedDelay = 5000)
    public void flush() {
        Map<Long, MetricsBucket> batch;
        synchronized (this) {
            if (pending.isEmpty()) return;
            batch = pending;
            pending = new HashMap<>();
        }
        try {
            store.merge(batch);
        } catch (RuntimeException exception) {
            // Redis가 잠깐 끊겨도 기록을 잃지 않도록 상자에 되돌려 다음 번에 다시 보냄.
            log.warn("API 집계를 저장하지 못해 다음에 다시 보냅니다. reason={}", exception.getMessage());
            synchronized (this) {
                batch.forEach((bucket, data) -> pending.computeIfAbsent(bucket, key -> new MetricsBucket()).add(data));
            }
        }
    }
}
