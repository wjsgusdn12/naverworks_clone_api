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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// DB의 1분·5분 칸과 1시간 요약을 읽어 대시보드 숫자로 정리(모두 DB라 서버·Redis를 재시작해도 남음).
// - 숫자 카드와 순위 합계: 시·일·주·월 모두 빠짐없는 1시간 요약 기준(서로 항상 맞아떨어짐).
// - 그래프: 이번 시간 1분, 오늘 5분 칸. 잘게 쪼갠 기록이 없는 구간(예: 기능 추가 전)은
//   큰 단위 기록(5분·1시간 합계)으로 채워 묶음 첫 칸에 점 하나로 표시.
// 기간은 "지금부터 거꾸로"가 아니라 달력 기준으로 자름(한국 시간 기본).
// - 이번 시간: 14:00~14:59 (1분 점 60개)
// - 오늘: 00:00~23:59 (5분 점 288개)
// - 이번 주: 일요일 00시~토요일 23시 (1시간 점 168개)
// - 이번 달: 1일~말일 (1시간 점 672~744개)
// 아직 오지 않은 시간의 점도 자리는 만들어 두고(값 0), 화면이 "기록 전"으로 비워 그림.
@Service
public class ApiDashboardService {
    static final int TOP_COUNT = 5;
    // 몇 번 안 불린 API가 "가장 느림"에 오르지 않도록 최소 호출 수.
    static final int SLOW_MIN_REQUESTS = 5;
    private static final long BUCKETS_PER_HOUR = 3600 / ApiMetricsRecorder.BUCKET_SECONDS;
    static final int DAY_BUCKETS = 24 * (int) BUCKETS_PER_HOUR;
    static final int WEEK_HOURS = 24 * 7;
    static final int HOUR_MINUTES = 60;
    static final int DAY_HOURS = 24;
    private static final int MINUTES_PER_BUCKET = (int) (ApiMetricsRecorder.BUCKET_SECONDS / 60);

    private final ApiMetricsRecorder recorder;
    private final ApiMetricsStore store;
    private final ServerStatusReporter reporter;
    private final DataSource dataSource;
    private final ApiMetricHourlyRepository hourlyRepository;
    private final ApiMetricBucketRepository bucketRepository;
    // 하루·한 주를 자르는 기준 시간대
    private final ZoneId zone;

    public ApiDashboardService(ApiMetricsRecorder recorder, ApiMetricsStore store, ServerStatusReporter reporter,
            DataSource dataSource, ApiMetricHourlyRepository hourlyRepository,
            ApiMetricBucketRepository bucketRepository, @Value("${app.monitoring.zone:Asia/Seoul}") String zone) {
        this.recorder = recorder;
        this.store = store;
        this.reporter = reporter;
        this.dataSource = dataSource;
        this.hourlyRepository = hourlyRepository;
        this.bucketRepository = bucketRepository;
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
            weekTrend.add(point(start, data, 60));
        }

        // 이번 달: 1시간 요약 그대로(1일 0시~말일 23시)
        Instant monthEnd = monthFirstDay.plusMonths(1).atStartOfDay(zone).toInstant();
        MetricsBucket thisMonth = new MetricsBucket();
        List<ApiDashboardResponse.TrendPoint> monthTrend = new ArrayList<>();
        List<Instant> monthBins = new ArrayList<>();
        for (Instant hour = monthStart; hour.isBefore(monthEnd); hour = hour.plus(Duration.ofHours(1))) {
            MetricsBucket data = weekHours.getOrDefault(hour, new MetricsBucket());
            thisMonth.add(data);
            monthTrend.add(point(hour, data, 60));
            monthBins.add(hour);
        }

        // 숫자 카드: 이번 시간·오늘도 1시간 요약에서(1분·5분 칸은 기능 추가 전 기록이 없을 수 있음).
        Instant dayEnd = dayStart.plus(Duration.ofHours(DAY_HOURS));
        MetricsBucket thisHour = new MetricsBucket();
        thisHour.add(weekHours.getOrDefault(hourStart, new MetricsBucket()));
        MetricsBucket today = new MetricsBucket();
        for (int hour = 0; hour < DAY_HOURS; hour++) {
            today.add(weekHours.getOrDefault(dayStart.plus(Duration.ofHours(hour)), new MetricsBucket()));
        }

        // 오늘 0시부터 지금까지의 5분 칸, 이번 시간 정각부터 지금까지의 1분 칸(DB가 돌려 쓰며 보관)
        long dayFirstBucket = ApiMetricsRecorder.bucketOf(dayStart);
        Map<Long, MetricsBucket> buckets = bucketRepository.read(ApiMetricBucketRepository.Ring.FIVE_MINUTES,
                dayFirstBucket, ApiMetricsRecorder.bucketOf(now));
        long hourFirstMinute = ApiMetricsRecorder.minuteOf(hourStart);
        Map<Long, MetricsBucket> minutes = bucketRepository.read(ApiMetricBucketRepository.Ring.ONE_MINUTE,
                hourFirstMinute, ApiMetricsRecorder.minuteOf(now));
        long hourFirstBucket = ApiMetricsRecorder.bucketOf(hourStart);

        // 이번 시간 그래프: 1분 칸 60개. 1분 기록이 없는 5분 구간은 5분 칸으로 채움.
        FineSeries hourFine = fineSeries(HOUR_MINUTES, MINUTES_PER_BUCKET, 1, MINUTES_PER_BUCKET,
                index -> Instant.ofEpochSecond((hourFirstMinute + index) * ApiMetricsRecorder.MINUTE_SECONDS),
                index -> minutes.get(hourFirstMinute + index), group -> buckets.get(hourFirstBucket + group));
        // 오늘 그래프: 5분 칸 288개. 5분 기록이 없는 시간은 1시간 요약으로 채움.
        FineSeries dayFine = fineSeries(DAY_BUCKETS, (int) BUCKETS_PER_HOUR, MINUTES_PER_BUCKET, 60,
                index -> Instant.ofEpochSecond((dayFirstBucket + index) * ApiMetricsRecorder.BUCKET_SECONDS),
                index -> buckets.get(dayFirstBucket + index),
                group -> weekHours.get(dayStart.plus(Duration.ofHours(group))));
        List<ApiDashboardResponse.TrendPoint> hourTrend = hourFine.points();
        List<ApiDashboardResponse.TrendPoint> dayTrend = dayFine.points();
        List<Instant> hourBins = hourFine.bins();
        List<Instant> dayBins = dayFine.bins();

        // 이번 주·이번 달 순위: DB의 API별 1시간 요약을 기간 합계로 읽음.
        // 작은 추이선은 이번 주는 하루 단위, 이번 달은 1시간 단위.
        LocalDate weekFirstDay = weekStart.atZone(zone).toLocalDate();
        Instant weekEnd = weekFirstDay.plusDays(7).atStartOfDay(zone).toInstant();
        List<Instant> weekBins = dayStarts(weekFirstDay, 7);

        // 이번 시간·오늘 순위도 합계는 1시간 요약 기준. 작은 추이선은 그래프와 같은 칸(빈 구간은 큰 단위로 채움).
        ApiDashboardResponse.Ranking hourRanking = ranking(
                hourlyRepository.readEndpointTotals(hourStart, hourStart.plus(Duration.ofHours(1))), hourBins,
                names -> hourFine.series());
        ApiDashboardResponse.Ranking dayRanking = ranking(hourlyRepository.readEndpointTotals(dayStart, dayEnd),
                dayBins, names -> daySeriesWithHourly(dayFine, dayStart, dayEnd, names));
        ApiDashboardResponse.Ranking weekRanking = ranking(hourlyRepository.readEndpointTotals(weekStart, weekEnd),
                weekBins, names -> dailySeries(weekStart, weekEnd, weekFirstDay, weekBins.size(), names));
        ApiDashboardResponse.Ranking monthRanking = ranking(hourlyRepository.readEndpointTotals(monthStart, monthEnd),
                monthBins, names -> hourlySeries(monthStart, monthEnd, monthBins.size(), names));

        List<ApiDashboardResponse.Server> servers = store.servers(now).stream()
                .map(status -> new ApiDashboardResponse.Server(status.instanceId(), status.startedAt(),
                        Duration.between(status.startedAt(), now).toSeconds(), status.heapUsedMb(), status.heapMaxMb(),
                        status.realtimeConnections(), status.instanceId().equals(reporter.instanceId())))
                .toList();

        return new ApiDashboardResponse(now, zone.getId(), store.mode(), databaseStatus(), servers, summary(thisHour),
                summary(today), summary(thisWeek), summary(thisMonth), hourTrend, dayTrend, weekTrend, monthTrend,
                hourRanking, dayRanking, weekRanking, monthRanking);
    }

    // 잘게 쪼갠 그래프 한 벌: 점, 칸 시작 시각, API별 [호출 수, 걸린 시간 합](순위 옆 작은 추이선용),
    // 큰 단위로 채운 묶음 번호(fallbackGroups).
    private record FineSeries(List<ApiDashboardResponse.TrendPoint> points, List<Instant> bins,
            Map<String, long[][]> series, Set<Integer> fallbackGroups) {
    }

    // count개의 작은 칸으로 그래프를 만듦. perGroup개씩 묶은 구간에 작은 칸 기록이 하나도 없고
    // 큰 칸(group) 기록이 있으면, 큰 칸 값을 묶음 첫 칸에 넣고(spanMinutes = groupMinutes) 나머지는 0으로 표시.
    private static FineSeries fineSeries(int count, int perGroup, int fineMinutes, int groupMinutes,
            IntFunction<Instant> startOf, IntFunction<MetricsBucket> fineAt, IntFunction<MetricsBucket> groupAt) {
        List<ApiDashboardResponse.TrendPoint> points = new ArrayList<>();
        List<Instant> bins = new ArrayList<>();
        Map<String, long[][]> series = new HashMap<>();
        Set<Integer> fallbackGroups = new HashSet<>();
        for (int group = 0; group * perGroup < count; group++) {
            int first = group * perGroup;
            int last = Math.min(first + perGroup, count);
            boolean fineEmpty = IntStream.range(first, last)
                    .allMatch(index -> fineAt.apply(index) == null || fineAt.apply(index).isEmpty());
            MetricsBucket coarse = groupAt.apply(group);
            boolean useCoarse = fineEmpty && coarse != null && !coarse.isEmpty();
            if (useCoarse) fallbackGroups.add(group);
            for (int index = first; index < last; index++) {
                Instant start = startOf.apply(index);
                bins.add(start);
                if (useCoarse) {
                    MetricsBucket data = index == first ? coarse : new MetricsBucket();
                    points.add(point(start, data, index == first ? groupMinutes : 0));
                    if (index == first) collect(series, coarse, index, count);
                    continue;
                }
                MetricsBucket data = fineAt.apply(index) == null ? new MetricsBucket() : fineAt.apply(index);
                points.add(point(start, data, fineMinutes));
                collect(series, data, index, count);
            }
        }
        return new FineSeries(points, bins, series, fallbackGroups);
    }

    // 오늘 작은 추이선: 1시간 요약으로 채운 시간은 1시간 합계 행에 API별 값이 없으므로 DB의 API별 1시간 기록을 더함.
    private Map<String, long[][]> daySeriesWithHourly(FineSeries fine, Instant dayStart, Instant dayEnd,
            Set<String> endpoints) {
        Map<String, long[][]> series = new HashMap<>();
        fine.series().forEach((endpoint, values) -> series.put(endpoint,
                new long[][] { values[0].clone(), values[1].clone() }));
        if (fine.fallbackGroups().isEmpty()) return series;
        int size = fine.bins().size();
        for (ApiMetricHourlyRepository.EndpointHour row : hourlyRepository.readEndpointHours(dayStart, dayEnd,
                endpoints)) {
            int hour = (int) Duration.between(dayStart, row.hourStart()).toHours();
            if (!fine.fallbackGroups().contains(hour)) continue;
            int index = hour * (int) BUCKETS_PER_HOUR;
            long[][] values = series.computeIfAbsent(row.endpoint(), key -> new long[2][size]);
            values[0][index] += row.requests();
            values[1][index] += row.durationSumMs();
        }
        return series;
    }

    // 한 칸(index)의 API별 [호출 수, 걸린 시간 합]을 series에 더함.
    private static void collect(Map<String, long[][]> series, MetricsBucket data, int index, int size) {
        data.getEndpoints().forEach((endpoint, stats) -> {
            long[][] values = series.computeIfAbsent(endpoint, key -> new long[2][size]);
            values[0][index] += stats.getCount();
            values[1][index] += stats.getDurationSumMs();
        });
    }

    // firstDay부터 days일의 0시 시각 목록
    private List<Instant> dayStarts(LocalDate firstDay, int days) {
        List<Instant> starts = new ArrayList<>();
        for (int day = 0; day < days; day++) starts.add(firstDay.plusDays(day).atStartOfDay(zone).toInstant());
        return starts;
    }

    // DB의 API별 1시간 기록을 하루 단위로 묶음(고른 API만).
    private Map<String, long[][]> dailySeries(Instant from, Instant to, LocalDate firstDay, int days,
            Set<String> endpoints) {
        Map<String, long[][]> series = new HashMap<>();
        for (ApiMetricHourlyRepository.EndpointHour row : hourlyRepository.readEndpointHours(from, to, endpoints)) {
            int day = (int) ChronoUnit.DAYS.between(firstDay, row.hourStart().atZone(zone).toLocalDate());
            if (day < 0 || day >= days) continue;
            long[][] values = series.computeIfAbsent(row.endpoint(), key -> new long[2][days]);
            values[0][day] += row.requests();
            values[1][day] += row.durationSumMs();
        }
        return series;
    }

    // DB의 API별 1시간 기록을 from부터 1시간 칸 그대로(고른 API만).
    private Map<String, long[][]> hourlySeries(Instant from, Instant to, int hours, Set<String> endpoints) {
        Map<String, long[][]> series = new HashMap<>();
        for (ApiMetricHourlyRepository.EndpointHour row : hourlyRepository.readEndpointHours(from, to, endpoints)) {
            int hour = (int) Duration.between(from, row.hourStart()).toHours();
            if (hour < 0 || hour >= hours) continue;
            long[][] values = series.computeIfAbsent(row.endpoint(), key -> new long[2][hours]);
            values[0][hour] += row.requests();
            values[1][hour] += row.durationSumMs();
        }
        return series;
    }

    // 기간의 API별 합계로 순위를 만듦: 많이 호출된 5개, 평균이 느린 5개(5회 이상 호출만, 없으면 전부 중에서).
    // 작은 추이선은 순위에 든 API만 읽어 옴(seriesOf).
    private static ApiDashboardResponse.Ranking ranking(Map<String, MetricsBucket.EndpointStats> totals,
            List<Instant> bins, Function<Set<String>, Map<String, long[][]>> seriesOf) {
        List<Map.Entry<String, MetricsBucket.EndpointStats>> entries = new ArrayList<>(totals.entrySet());
        List<String> top = entries.stream()
                .sorted(Comparator.comparingLong(
                        (Map.Entry<String, MetricsBucket.EndpointStats> entry) -> entry.getValue().getCount()).reversed())
                .limit(TOP_COUNT).map(Map.Entry::getKey).toList();
        List<Map.Entry<String, MetricsBucket.EndpointStats>> slowCandidates = entries.stream()
                .filter(entry -> entry.getValue().getCount() >= SLOW_MIN_REQUESTS).toList();
        List<String> slow = (slowCandidates.isEmpty() ? entries : slowCandidates).stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, MetricsBucket.EndpointStats> entry) -> average(
                        entry.getValue().getDurationSumMs(), entry.getValue().getCount())).reversed())
                .limit(TOP_COUNT).map(Map.Entry::getKey).toList();
        Set<String> needed = new LinkedHashSet<>(top);
        needed.addAll(slow);
        Map<String, long[][]> series = seriesOf.apply(needed);
        Function<String, ApiDashboardResponse.EndpointStat> toStat = endpoint -> {
            MetricsBucket.EndpointStats stats = totals.get(endpoint);
            long[][] values = series.getOrDefault(endpoint, new long[2][bins.size()]);
            List<Long> requests = new ArrayList<>();
            List<Long> averages = new ArrayList<>();
            for (int index = 0; index < bins.size(); index++) {
                requests.add(values[0][index]);
                averages.add(average(values[1][index], values[0][index]));
            }
            return new ApiDashboardResponse.EndpointStat(endpoint, stats.getCount(), stats.getErrors(),
                    average(stats.getDurationSumMs(), stats.getCount()), requests, averages);
        };
        return new ApiDashboardResponse.Ranking(bins, top.stream().map(toStat).toList(),
                slow.stream().map(toStat).toList());
    }

    // startBucket부터 시작하는 5분 구간 하나를 추이 한 점으로.
    private static ApiDashboardResponse.TrendPoint point(Instant start, MetricsBucket data, int spanMinutes) {
        return new ApiDashboardResponse.TrendPoint(start, data.getCount(),
                data.getClientErrors() + data.getServerErrors(), average(data.getDurationSumMs(), data.getCount()),
                data.p95Ms(), spanMinutes);
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
