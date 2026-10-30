package com.jhwjhw.naverworkscloneapi.monitoring;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    // 일주일 그래프의 1시간 점 개수
    static final int WEEK_HOURS = 24 * 7;

    private final ApiMetricsRecorder recorder;
    private final ApiMetricsStore store;
    private final ServerStatusReporter reporter;
    private final DataSource dataSource;
    private final ApiMetricHourlyRepository hourlyRepository;

    public ApiDashboardResponse dashboard() {
        Instant now = Instant.now();
        // 이 서버가 아직 보내지 않은 최근 기록과 상태를 먼저 반영(다른 서버의 1시간 요약은 최대 1분 늦게 반영).
        recorder.flush();
        recorder.flushHourly();
        reporter.report();

        // 일주일: DB의 1시간 요약 168칸(지금 시간 포함)
        Instant thisHour = ApiMetricsRecorder.hourOf(now);
        Instant weekFrom = thisHour.minus(Duration.ofHours(WEEK_HOURS - 1));
        Map<Instant, MetricsBucket> weekHours = hourlyRepository.readTotals(weekFrom);
        MetricsBucket lastWeek = new MetricsBucket();
        List<ApiDashboardResponse.TrendPoint> weekTrend = new ArrayList<>();
        for (int hour = 0; hour < WEEK_HOURS; hour++) {
            Instant start = weekFrom.plus(Duration.ofHours(hour));
            MetricsBucket data = weekHours.getOrDefault(start, new MetricsBucket());
            lastWeek.add(data);
            weekTrend.add(point(start, data));
        }

        long current = ApiMetricsRecorder.bucketOf(now);
        long from = current - BUCKETS_PER_DAY + 1;
        Map<Long, MetricsBucket> buckets = store.read(from, current);

        MetricsBucket lastHour = new MetricsBucket();
        MetricsBucket lastDay = new MetricsBucket();
        List<ApiDashboardResponse.TrendPoint> trend = new ArrayList<>();
        List<ApiDashboardResponse.TrendPoint> recentTrend = new ArrayList<>();
        // 엔드포인트별 1시간 단위 [호출 수, 걸린 시간 합] — 목록 옆 작은 추이선용.
        Map<String, long[][]> hourlyByEndpoint = new HashMap<>();
        // 24시간을 1시간(5분 칸 12개)씩 묶어 추이를 만듦. 마지막 1시간은 5분 칸 그대로도 담음.
        for (int hour = 0; hour < 24; hour++) {
            MetricsBucket hourly = new MetricsBucket();
            long start = from + hour * BUCKETS_PER_HOUR;
            for (long bucket = start; bucket < start + BUCKETS_PER_HOUR; bucket++) {
                MetricsBucket data = buckets.get(bucket);
                if (bucket > current - BUCKETS_PER_HOUR) {
                    recentTrend.add(point(bucket, data == null ? new MetricsBucket() : data));
                }
                if (data == null) continue;
                hourly.add(data);
                if (bucket > current - BUCKETS_PER_HOUR) lastHour.add(data);
            }
            lastDay.add(hourly);
            trend.add(point(start, hourly));
            int hourIndex = hour;
            hourly.getEndpoints().forEach((endpoint, stats) -> {
                long[][] series = hourlyByEndpoint.computeIfAbsent(endpoint, key -> new long[2][24]);
                series[0][hourIndex] = stats.getCount();
                series[1][hourIndex] = stats.getDurationSumMs();
            });
        }

        List<ApiDashboardResponse.EndpointStat> endpoints = lastDay.getEndpoints().entrySet().stream()
                .map(entry -> {
                    long[][] series = hourlyByEndpoint.getOrDefault(entry.getKey(), new long[2][24]);
                    List<Long> hourlyRequests = new ArrayList<>();
                    List<Long> hourlyAvgMs = new ArrayList<>();
                    for (int hour = 0; hour < 24; hour++) {
                        hourlyRequests.add(series[0][hour]);
                        hourlyAvgMs.add(average(series[1][hour], series[0][hour]));
                    }
                    return new ApiDashboardResponse.EndpointStat(entry.getKey(), entry.getValue().getCount(),
                            entry.getValue().getErrors(),
                            average(entry.getValue().getDurationSumMs(), entry.getValue().getCount()), hourlyRequests,
                            hourlyAvgMs);
                })
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
                summary(lastDay), summary(lastWeek), trend, recentTrend, weekTrend, top, slow);
    }

    // startBucket부터 시작하는 구간 하나를 추이 한 점으로.
    private static ApiDashboardResponse.TrendPoint point(long startBucket, MetricsBucket data) {
        return point(Instant.ofEpochSecond(startBucket * ApiMetricsRecorder.BUCKET_SECONDS), data);
    }

    private static ApiDashboardResponse.TrendPoint point(Instant start, MetricsBucket data) {
        return new ApiDashboardResponse.TrendPoint(start, data.getCount(),
                data.getClientErrors() + data.getServerErrors(), average(data.getDurationSumMs(), data.getCount()),
                data.p95Ms());
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
