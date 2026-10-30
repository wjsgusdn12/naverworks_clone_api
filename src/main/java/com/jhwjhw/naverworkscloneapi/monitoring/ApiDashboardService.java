package com.jhwjhw.naverworkscloneapi.monitoring;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 집계판의 5분 칸들을 읽어 대시보드 숫자(1시간·24시간 합계, 추이, 순위, 서버 목록)로 정리.
@Service
@RequiredArgsConstructor
public class ApiDashboardService {
    static final int TOP_COUNT = 5;
    // 몇 번 안 불린 API가 "가장 느림"에 오르지 않도록 최소 호출 수.
    static final int SLOW_MIN_REQUESTS = 5;
    private static final long BUCKETS_PER_HOUR = 3600 / ApiMetricsRecorder.BUCKET_SECONDS;
    private static final long BUCKETS_PER_DAY = BUCKETS_PER_HOUR * 24;

    private final ApiMetricsRecorder recorder;
    private final ApiMetricsStore store;
    private final ServerStatusReporter reporter;
    private final DataSource dataSource;

    public ApiDashboardResponse dashboard() {
        Instant now = Instant.now();
        // 이 서버가 아직 보내지 않은 최근 기록과 상태를 먼저 반영.
        recorder.flush();
        reporter.report();

        long current = ApiMetricsRecorder.bucketOf(now);
        long from = current - BUCKETS_PER_DAY + 1;
        Map<Long, MetricsBucket> buckets = store.read(from, current);

        MetricsBucket lastHour = new MetricsBucket();
        MetricsBucket lastDay = new MetricsBucket();
        List<ApiDashboardResponse.TrendPoint> trend = new ArrayList<>();
        // 24시간을 1시간(5분 칸 12개)씩 묶어 추이를 만듦.
        for (int hour = 0; hour < 24; hour++) {
            MetricsBucket hourly = new MetricsBucket();
            long start = from + hour * BUCKETS_PER_HOUR;
            for (long bucket = start; bucket < start + BUCKETS_PER_HOUR; bucket++) {
                MetricsBucket data = buckets.get(bucket);
                if (data == null) continue;
                hourly.add(data);
                if (bucket > current - BUCKETS_PER_HOUR) lastHour.add(data);
            }
            lastDay.add(hourly);
            trend.add(new ApiDashboardResponse.TrendPoint(Instant.ofEpochSecond(start * ApiMetricsRecorder.BUCKET_SECONDS),
                    hourly.getCount(), hourly.getClientErrors() + hourly.getServerErrors()));
        }

        List<ApiDashboardResponse.EndpointStat> endpoints = lastDay.getEndpoints().entrySet().stream()
                .map(entry -> new ApiDashboardResponse.EndpointStat(entry.getKey(), entry.getValue().getCount(),
                        entry.getValue().getErrors(), average(entry.getValue().getDurationSumMs(), entry.getValue().getCount())))
                .toList();
        List<ApiDashboardResponse.EndpointStat> top = endpoints.stream()
                .sorted(Comparator.comparingLong(ApiDashboardResponse.EndpointStat::requests).reversed())
                .limit(TOP_COUNT).toList();
        List<ApiDashboardResponse.EndpointStat> slowCandidates = endpoints.stream()
                .filter(stat -> stat.requests() >= SLOW_MIN_REQUESTS).toList();
        List<ApiDashboardResponse.EndpointStat> slow = (slowCandidates.isEmpty() ? endpoints : slowCandidates).stream()
                .sorted(Comparator.comparingLong(ApiDashboardResponse.EndpointStat::avgMs).reversed())
                .limit(TOP_COUNT).toList();

        List<ApiDashboardResponse.Server> servers = store.servers(now).stream()
                .map(status -> new ApiDashboardResponse.Server(status.instanceId(), status.startedAt(),
                        Duration.between(status.startedAt(), now).toSeconds(), status.heapUsedMb(), status.heapMaxMb(),
                        status.realtimeConnections(), status.instanceId().equals(reporter.instanceId())))
                .toList();

        return new ApiDashboardResponse(now, store.mode(), databaseStatus(), servers, summary(lastHour),
                summary(lastDay), trend, top, slow);
    }

    private static ApiDashboardResponse.Summary summary(MetricsBucket bucket) {
        double errorRate = bucket.getCount() == 0 ? 0
                : Math.round(bucket.getServerErrors() * 1000.0 / bucket.getCount()) / 10.0;
        return new ApiDashboardResponse.Summary(bucket.getCount(), bucket.getClientErrors(), bucket.getServerErrors(),
                errorRate, average(bucket.getDurationSumMs(), bucket.getCount()), bucket.p95Ms());
    }

    private static long average(long sum, long count) {
        return count == 0 ? 0 : Math.round((double) sum / count);
    }

    // DB에 실제로 연결해 2초 안에 응답하는지 확인.
    private String databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (SQLException exception) {
            return "DOWN";
        }
    }
}
