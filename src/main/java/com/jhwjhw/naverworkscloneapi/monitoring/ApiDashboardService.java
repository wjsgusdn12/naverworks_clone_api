package com.jhwjhw.naverworkscloneapi.monitoring;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// 집계판의 5분 칸과 DB의 1시간 요약을 읽어 대시보드 숫자로 정리.
// 기간은 "지금부터 거꾸로"가 아니라 달력 기준으로 자름(한국 시간 기본).
// - 이번 시간: 14:00~14:59 (5분 점 12개)
// - 오늘: 00:00~23:59 (1시간 점 24개)
// - 이번 주: 일요일 00시~토요일 23시 (1시간 점 168개)
// - 이번 달: 1일~말일 (1일 점 28~31개)
// 아직 오지 않은 시간의 점도 자리는 만들어 두고(값 0), 화면이 "기록 전"으로 비워 그림.
@Service
public class ApiDashboardService {
    static final int TOP_COUNT = 5;
    // 몇 번 안 불린 API가 "가장 느림"에 오르지 않도록 최소 호출 수.
    static final int SLOW_MIN_REQUESTS = 5;
    private static final long BUCKETS_PER_HOUR = 3600 / ApiMetricsRecorder.BUCKET_SECONDS;
    static final int DAY_HOURS = 24;
    static final int WEEK_HOURS = 24 * 7;

    private final ApiMetricsRecorder recorder;
    private final ApiMetricsStore store;
    private final ServerStatusReporter reporter;
    private final DataSource dataSource;
    private final ApiMetricHourlyRepository hourlyRepository;
    // 하루·한 주를 자르는 기준 시간대
    private final ZoneId zone;

    public ApiDashboardService(ApiMetricsRecorder recorder, ApiMetricsStore store, ServerStatusReporter reporter,
            DataSource dataSource, ApiMetricHourlyRepository hourlyRepository,
            @Value("${app.monitoring.zone:Asia/Seoul}") String zone) {
        this.recorder = recorder;
        this.store = store;
        this.reporter = reporter;
        this.dataSource = dataSource;
        this.hourlyRepository = hourlyRepository;
        this.zone = ZoneId.of(zone);
    }

    public ApiDashboardResponse dashboard() {
        Instant now = Instant.now();
        // 이 서버가 아직 보내지 않은 최근 기록과 상태를 먼저 반영(다른 서버의 1시간 요약은 최대 1분 늦게 반영).
        recorder.flush();
        recorder.flushHourly();
        reporter.report();

        ZonedDateTime local = now.atZone(zone);
        Instant hourStart = local.truncatedTo(ChronoUnit.HOURS).toInstant();
        Instant dayStart = local.toLocalDate().atStartOfDay(zone).toInstant();
        Instant weekStart = local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                .atStartOfDay(zone).toInstant();
        LocalDate monthFirstDay = local.toLocalDate().withDayOfMonth(1);
        Instant monthStart = monthFirstDay.atStartOfDay(zone).toInstant();

        // 이번 주·이번 달은 DB의 1시간 요약에서 읽음(주가 지난달에 걸칠 수 있어 더 이른 쪽부터 한 번에 읽음).
        Map<Instant, MetricsBucket> weekHours = hourlyRepository
                .readTotals(weekStart.isBefore(monthStart) ? weekStart : monthStart);
        MetricsBucket thisWeek = new MetricsBucket();
        List<ApiDashboardResponse.TrendPoint> weekTrend = new ArrayList<>();
        for (int hour = 0; hour < WEEK_HOURS; hour++) {
            Instant start = weekStart.plus(Duration.ofHours(hour));
            MetricsBucket data = weekHours.getOrDefault(start, new MetricsBucket());
            thisWeek.add(data);
            weekTrend.add(point(start, data));
        }

        // 이번 달: 1시간 요약을 하루씩 묶음(그 날의 0시~23시)
        MetricsBucket thisMonth = new MetricsBucket();
        List<ApiDashboardResponse.TrendPoint> monthTrend = new ArrayList<>();
        for (int day = 0; day < monthFirstDay.lengthOfMonth(); day++) {
            ZonedDateTime dateStart = monthFirstDay.plusDays(day).atStartOfDay(zone);
            Instant nextDay = dateStart.plusDays(1).toInstant();
            MetricsBucket daily = new MetricsBucket();
            for (Instant hour = dateStart.toInstant(); hour.isBefore(nextDay); hour = hour.plus(Duration.ofHours(1))) {
                MetricsBucket data = weekHours.get(hour);
                if (data != null) daily.add(data);
            }
            thisMonth.add(daily);
            monthTrend.add(point(dateStart.toInstant(), daily));
        }

        // 오늘 0시부터 지금까지의 5분 칸(집계판은 25시간치를 갖고 있어 하루를 모두 덮음)
        long dayFirstBucket = ApiMetricsRecorder.bucketOf(dayStart);
        long hourFirstBucket = ApiMetricsRecorder.bucketOf(hourStart);
        Map<Long, MetricsBucket> buckets = store.read(dayFirstBucket, ApiMetricsRecorder.bucketOf(now));

        // 이번 시간: 5분 칸 12개 그대로
        MetricsBucket thisHour = new MetricsBucket();
        List<ApiDashboardResponse.TrendPoint> hourTrend = new ArrayList<>();
        for (long bucket = hourFirstBucket; bucket < hourFirstBucket + BUCKETS_PER_HOUR; bucket++) {
            MetricsBucket data = buckets.getOrDefault(bucket, new MetricsBucket());
            thisHour.add(data);
            hourTrend.add(point(bucket, data));
        }

        // 오늘: 5분 칸 12개씩 묶어 1시간 점 24개. API별 1시간 [호출 수, 걸린 시간 합]도 함께 모음(작은 추이선용).
        MetricsBucket today = new MetricsBucket();
        List<ApiDashboardResponse.TrendPoint> dayTrend = new ArrayList<>();
        Map<String, long[][]> hourlyByEndpoint = new HashMap<>();
        for (int hour = 0; hour < DAY_HOURS; hour++) {
            MetricsBucket hourly = new MetricsBucket();
            long start = dayFirstBucket + hour * BUCKETS_PER_HOUR;
            for (long bucket = start; bucket < start + BUCKETS_PER_HOUR; bucket++) {
                MetricsBucket data = buckets.get(bucket);
                if (data != null) hourly.add(data);
            }
            today.add(hourly);
            dayTrend.add(point(start, hourly));
            int hourIndex = hour;
            hourly.getEndpoints().forEach((endpoint, stats) -> {
                long[][] series = hourlyByEndpoint.computeIfAbsent(endpoint, key -> new long[2][DAY_HOURS]);
                series[0][hourIndex] = stats.getCount();
                series[1][hourIndex] = stats.getDurationSumMs();
            });
        }

        // API 순위는 오늘 기준
        List<ApiDashboardResponse.EndpointStat> endpoints = today.getEndpoints().entrySet().stream()
                .map(entry -> {
                    long[][] series = hourlyByEndpoint.getOrDefault(entry.getKey(), new long[2][DAY_HOURS]);
                    List<Long> hourlyRequests = new ArrayList<>();
                    List<Long> hourlyAvgMs = new ArrayList<>();
                    for (int hour = 0; hour < DAY_HOURS; hour++) {
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

        return new ApiDashboardResponse(now, zone.getId(), store.mode(), databaseStatus(), servers, summary(thisHour),
                summary(today), summary(thisWeek), summary(thisMonth), hourTrend, dayTrend, weekTrend, monthTrend, top, slow);
    }

    // startBucket부터 시작하는 5분 구간 하나를 추이 한 점으로.
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
