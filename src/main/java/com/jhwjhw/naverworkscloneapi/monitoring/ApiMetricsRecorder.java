package com.jhwjhw.naverworkscloneapi.monitoring;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 요청 기록을 이 서버의 작은 상자에 모았다가 한꺼번에 보냄. 요청마다 저장소에 다녀오면 응답이 느려지기 때문.
// - pending: 5분 칸 → 5초마다 집계판(ApiMetricsStore, 최근 24시간)으로
// - hourlyPending: 1시간 칸 → 1분마다 DB(ApiMetricHourlyRepository, 일주일 그래프)로
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiMetricsRecorder {
    // 한 칸의 길이(초). 5분 단위로 모아 24시간이면 288칸.
    public static final long BUCKET_SECONDS = 300;
    public static final long HOUR_SECONDS = 3600;

    private final ApiMetricsStore store;
    private final ApiMetricHourlyRepository hourlyRepository;
    private Map<Long, MetricsBucket> pending = new HashMap<>();
    private Map<Instant, MetricsBucket> hourlyPending = new HashMap<>();

    public static long bucketOf(Instant at) {
        return at.getEpochSecond() / BUCKET_SECONDS;
    }

    // 그 시각이 속한 1시간의 시작 시각(예: 13:42 → 13:00)
    public static Instant hourOf(Instant at) {
        return Instant.ofEpochSecond(at.getEpochSecond() / HOUR_SECONDS * HOUR_SECONDS);
    }

    public synchronized void record(String endpoint, int status, long durationMs, Instant at) {
        pending.computeIfAbsent(bucketOf(at), key -> new MetricsBucket()).record(endpoint, status, durationMs);
        hourlyPending.computeIfAbsent(hourOf(at), key -> new MetricsBucket()).record(endpoint, status, durationMs);
    }

    // 모아 둔 5분 칸을 집계판에 보냄. 대시보드를 열 때도 먼저 불러 최신 기록이 빠지지 않게 함.
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

    // 모아 둔 1시간 칸을 DB에 더함. DB 쓰기를 줄이려고 1분에 한 번만.
    @Scheduled(fixedDelay = 60_000)
    public void flushHourly() {
        Map<Instant, MetricsBucket> batch;
        synchronized (this) {
            if (hourlyPending.isEmpty()) return;
            batch = hourlyPending;
            hourlyPending = new HashMap<>();
        }
        // 시간별로 따로 저장해, 실패한 시간만 되돌림(이미 저장된 시간을 두 번 더하지 않게).
        batch.forEach((hour, data) -> {
            try {
                hourlyRepository.addHour(hour, data);
            } catch (RuntimeException exception) {
                log.warn("1시간 요약을 DB에 저장하지 못해 다음에 다시 보냅니다. reason={}", exception.getMessage());
                synchronized (this) {
                    hourlyPending.computeIfAbsent(hour, key -> new MetricsBucket()).add(data);
                }
            }
        });
    }
}
