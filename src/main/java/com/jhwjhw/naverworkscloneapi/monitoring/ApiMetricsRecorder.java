package com.jhwjhw.naverworkscloneapi.monitoring;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 요청 기록을 이 서버의 작은 상자에 모았다가 한꺼번에 DB로 보냄. 요청마다 DB에 다녀오면 응답이 느려지기 때문.
// 둘 다 DB에 남으므로 서버·Redis를 재시작해도 집계가 사라지지 않음.
// - pending: 5분 칸 → 5초마다 DB(ApiMetricBucketRepository, 이번 시간·오늘 그래프)로
// - hourlyPending: 1시간 칸 → 1분마다 DB(ApiMetricHourlyRepository, 이번 주·이번 달 그래프)로
// 서버가 꺼질 때(재시작 포함)는 상자에 남은 것을 마지막으로 모두 보냄.
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiMetricsRecorder {
    // 한 칸의 길이(초). 5분 단위로 모아 24시간이면 288칸.
    public static final long BUCKET_SECONDS = 300;
    public static final long HOUR_SECONDS = 3600;

    private final ApiMetricBucketRepository bucketRepository;
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

    // 모아 둔 5분 칸을 DB에 더함. 대시보드를 열 때도 먼저 불러 최신 기록이 빠지지 않게 함.
    @Scheduled(fixedDelay = 5000)
    public void flush() {
        Map<Long, MetricsBucket> batch;
        synchronized (this) {
            if (pending.isEmpty()) return;
            batch = pending;
            pending = new HashMap<>();
        }
        // 칸별로 따로 저장해, 실패한 칸만 되돌림(이미 저장된 칸을 두 번 더하지 않게).
        batch.forEach((bucket, data) -> {
            try {
                bucketRepository.addBucket(bucket, data);
            } catch (RuntimeException exception) {
                // DB가 잠깐 끊겨도 기록을 잃지 않도록 상자에 되돌려 다음 번에 다시 보냄.
                log.warn("5분 집계를 DB에 저장하지 못해 다음에 다시 보냅니다. reason={}", exception.getMessage());
                synchronized (this) {
                    pending.computeIfAbsent(bucket, key -> new MetricsBucket()).add(data);
                }
            }
        });
    }

    // 서버가 꺼질 때 상자에 남은 기록을 마지막으로 저장(개발 중 자동 재시작 포함).
    @PreDestroy
    public void flushOnShutdown() {
        flush();
        flushHourly();
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
