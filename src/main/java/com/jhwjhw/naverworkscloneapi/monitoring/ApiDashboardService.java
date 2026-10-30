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
//   큰 단위 기록(5분·1시간 합계)을 그 구간의 칸마다 고르게 나눠 채우고 추정값(estimated)으로 표시.
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

    // 대시보드 기간 탭. 화면이 고른 기간 하나만 계산해 보냄(응답 크기·DB 조회를 줄임).
    public enum Range {
        HOUR, DAY, WEEK, MONTH
    }

    public ApiDashboardResponse dashboard(Range range) {
        Instant now = Instant.now();
        // 이 서버가 아직 보내지 않은 최근 기록과 상태를 먼저 반영(다른 서버의 1시간 요약은 최대 1분 늦게 반영).
        recorder.flush();
        recorder.flushHourly();
        reporter.report();

        ZonedDateTime local = now.atZone(zone);
        Period period = switch (range) {
            case HOUR -> hourPeriod(local, now);
            case DAY -> dayPeriod(local, now);
            case WEEK -> weekPeriod(local);
            case MONTH -> monthPeriod(local);
        };

        List<ApiDashboardResponse.Server> servers = store.servers(now).stream()
                .map(status -> new ApiDashboardResponse.Server(status.instanceId(), status.startedAt(),
                        Duration.between(status.startedAt(), now).toSeconds(), status.heapUsedMb(), status.heapMaxMb(),
                        status.realtimeConnections(), status.instanceId().equals(reporter.instanceId())))
                .toList();

        return new ApiDashboardResponse(now, zone.getId(), store.mode(), databaseStatus(), servers, range.name(),
                summary(period.total()), period.trend(), period.ranking());
    }

    // 한 기간의 계산 결과: 숫자 카드용 합계, 그래프 점, 순위.
    private record Period(MetricsBucket total, List<ApiDashboardResponse.TrendPoint> trend,
            ApiDashboardResponse.Ranking ranking) {
    }

    // 이번 시간: 숫자 카드·순위 합계는 1시간 요약, 그래프는 1분 칸 60개(1분 기록이 없는 5분 구간은 5분 칸으로 채움).
    private Period hourPeriod(ZonedDateTime local, Instant now) {
        Instant hourStart = local.truncatedTo(ChronoUnit.HOURS).toInstant();
        MetricsBucket total = hourlyRepository.readTotals(hourStart).getOrDefault(hourStart, new MetricsBucket());
        long hourFirstMinute = ApiMetricsRecorder.minuteOf(hourStart);
        long hourFirstBucket = ApiMetricsRecorder.bucketOf(hourStart);
        Map<Long, MetricsBucket> minutes = bucketRepository.read(ApiMetricBucketRepository.Ring.ONE_MINUTE,
                hourFirstMinute, ApiMetricsRecorder.minuteOf(now));
        Map<Long, MetricsBucket> buckets = bucketRepository.read(ApiMetricBucketRepository.Ring.FIVE_MINUTES,
                hourFirstBucket, ApiMetricsRecorder.bucketOf(now));
        FineSeries fine = fineSeries(HOUR_MINUTES, MINUTES_PER_BUCKET, 1, MINUTES_PER_BUCKET,
                index -> Instant.ofEpochSecond((hourFirstMinute + index) * ApiMetricsRecorder.MINUTE_SECONDS),
                index -> minutes.get(hourFirstMinute + index), group -> buckets.get(hourFirstBucket + group));
        ApiDashboardResponse.Ranking ranking = ranking(
                hourlyRepository.readEndpointTotals(hourStart, hourStart.plus(Duration.ofHours(1))), fine.bins(),
                names -> fine.series());
        return new Period(total, fine.points(), ranking);
    }

    // 오늘: 숫자 카드·순위 합계는 1시간 요약, 그래프는 5분 칸 288개(5분 기록이 없는 시간은 1시간 요약으로 채움).
    private Period dayPeriod(ZonedDateTime local, Instant now) {
        Instant dayStart = local.toLocalDate().atStartOfDay(zone).toInstant();
        Instant dayEnd = dayStart.plus(Duration.ofHours(DAY_HOURS));
        Map<Instant, MetricsBucket> hours = hourlyRepository.readTotals(dayStart);
        MetricsBucket total = new MetricsBucket();
        for (int hour = 0; hour < DAY_HOURS; hour++) {
            MetricsBucket data = hours.get(dayStart.plus(Duration.ofHours(hour)));
            if (data != null) total.add(data);
        }
        long dayFirstBucket = ApiMetricsRecorder.bucketOf(dayStart);
        Map<Long, MetricsBucket> buckets = bucketRepository.read(ApiMetricBucketRepository.Ring.FIVE_MINUTES,
                dayFirstBucket, ApiMetricsRecorder.bucketOf(now));
        FineSeries fine = fineSeries(DAY_BUCKETS, (int) BUCKETS_PER_HOUR, MINUTES_PER_BUCKET, 60,
                index -> Instant.ofEpochSecond((dayFirstBucket + index) * ApiMetricsRecorder.BUCKET_SECONDS),
                index -> buckets.get(dayFirstBucket + index), group -> hours.get(dayStart.plus(Duration.ofHours(group))));
        ApiDashboardResponse.Ranking ranking = ranking(hourlyRepository.readEndpointTotals(dayStart, dayEnd),
                fine.bins(), names -> daySeriesWithHourly(fine, dayStart, dayEnd, names));
        return new Period(total, fine.points(), ranking);
    }

    // 이번 주(일~토): 1시간 요약 168칸. 순위 옆 작은 추이선은 하루 단위.
    private Period weekPeriod(ZonedDateTime local) {
        LocalDate weekFirstDay = local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        Instant weekStart = weekFirstDay.atStartOfDay(zone).toInstant();
        Instant weekEnd = weekFirstDay.plusDays(7).atStartOfDay(zone).toInstant();
        HourlyTrend hourly = hourlyTrend(weekStart, weekEnd);
        List<Instant> bins = dayStarts(weekFirstDay, 7);
        ApiDashboardResponse.Ranking ranking = ranking(hourlyRepository.readEndpointTotals(weekStart, weekEnd), bins,
                names -> dailySeries(weekStart, weekEnd, weekFirstDay, bins.size(), names));
        return new Period(hourly.total(), hourly.points(), ranking);
    }

    // 이번 달(1일~말일): 1시간 요약 그대로. 순위 옆 작은 추이선도 1시간 단위.
    private Period monthPeriod(ZonedDateTime local) {
        LocalDate monthFirstDay = local.toLocalDate().withDayOfMonth(1);
        Instant monthStart = monthFirstDay.atStartOfDay(zone).toInstant();
        Instant monthEnd = monthFirstDay.plusMonths(1).atStartOfDay(zone).toInstant();
        HourlyTrend hourly = hourlyTrend(monthStart, monthEnd);
        List<Instant> bins = hourly.points().stream().map(ApiDashboardResponse.TrendPoint::startAt).toList();
        ApiDashboardResponse.Ranking ranking = ranking(hourlyRepository.readEndpointTotals(monthStart, monthEnd),
                bins, names -> hourlySeries(monthStart, monthEnd, bins.size(), names));
        return new Period(hourly.total(), hourly.points(), ranking);
    }

    private record HourlyTrend(MetricsBucket total, List<ApiDashboardResponse.TrendPoint> points) {
    }

    // [from, to) 기간을 1시간 요약 한 칸씩 점으로.
    private HourlyTrend hourlyTrend(Instant from, Instant to) {
        Map<Instant, MetricsBucket> hours = hourlyRepository.readTotals(from);
        MetricsBucket total = new MetricsBucket();
        List<ApiDashboardResponse.TrendPoint> points = new ArrayList<>();
        for (Instant hour = from; hour.isBefore(to); hour = hour.plus(Duration.ofHours(1))) {
            MetricsBucket data = hours.getOrDefault(hour, new MetricsBucket());
            total.add(data);
            points.add(point(hour, data, 60));
        }
        return new HourlyTrend(total, points);
    }

    // 잘게 쪼갠 그래프 한 벌: 점, 칸 시작 시각, API별 [호출 수, 걸린 시간 합](순위 옆 작은 추이선용),
    // 큰 단위로 채운 묶음 번호(fallbackGroups).
    private record FineSeries(List<ApiDashboardResponse.TrendPoint> points, List<Instant> bins,
            Map<String, long[][]> series, Set<Integer> fallbackGroups) {
    }

    // count개의 작은 칸으로 그래프를 만듦. perGroup개씩 묶은 구간에 작은 칸 기록이 하나도 없고
    // 큰 칸(group) 기록이 있으면, 큰 칸 값을 그 구간 칸마다 고르게 나눠 채움(추정값, 나눈 칸을 더하면 원래 합계).
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
                    MetricsBucket cell = splitBucket(coarse, last - first, index - first);
                    // 나눈 칸은 응답 시간 구간도 따로 나눠 p95가 흔들리므로 그 구간 전체의 p95를 그대로 씀.
                    points.add(new ApiDashboardResponse.TrendPoint(start, cell.getCount(),
                            cell.getClientErrors() + cell.getServerErrors(),
                            average(cell.getDurationSumMs(), cell.getCount()),
                            cell.getCount() == 0 ? 0 : coarse.p95Ms(), fineMinutes, latencyOf(cell), true));
                    collect(series, cell, index, count);
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
            int first = hour * (int) BUCKETS_PER_HOUR;
            int parts = (int) BUCKETS_PER_HOUR;
            long[][] values = series.computeIfAbsent(row.endpoint(), key -> new long[2][size]);
            // 그래프처럼 1시간 값을 5분 칸 12개에 고르게 나눔.
            long before = 0;
            for (int part = 0; part < parts; part++) {
                long requests = share(row.requests(), parts, part);
                values[0][first + part] += requests;
                values[1][first + part] += proportional(row.durationSumMs(), before, requests, row.requests());
                before += requests;
            }
        }
        return series;
    }

    // total을 parts칸에 고르게 나눈 index번째 몫(나머지는 앞 칸부터 1씩). 몫을 모두 더하면 total.
    private static long share(long total, int parts, int index) {
        return total / parts + (index < total % parts ? 1 : 0);
    }

    // total을 비율(앞 칸까지 누적 before, 이 칸 weight, 전체 weightTotal)로 나눈 몫. 몫을 모두 더하면 total.
    private static long proportional(long total, long before, long weight, long weightTotal) {
        if (weightTotal == 0) return 0;
        return Math.floorDiv(total * (before + weight), weightTotal) - Math.floorDiv(total * before, weightTotal);
    }

    // 큰 칸 하나를 parts칸으로 고르게 나눈 index번째 칸. 걸린 시간 합은 요청 수 비율대로 나눠 평균이 그대로 유지됨.
    private static MetricsBucket splitBucket(MetricsBucket coarse, int parts, int index) {
        MetricsBucket cell = new MetricsBucket();
        long before = 0;
        for (int part = 0; part < index; part++) before += share(coarse.getCount(), parts, part);
        long count = share(coarse.getCount(), parts, index);
        cell.addTotals(count, share(coarse.getClientErrors(), parts, index),
                share(coarse.getServerErrors(), parts, index),
                proportional(coarse.getDurationSumMs(), before, count, coarse.getCount()));
        long[] latency = coarse.getLatencyCounts();
        for (int bin = 0; bin < latency.length; bin++) cell.addLatency(bin, share(latency[bin], parts, index));
        coarse.getEndpoints().forEach((endpoint, stats) -> {
            long endpointBefore = 0;
            for (int part = 0; part < index; part++) endpointBefore += share(stats.getCount(), parts, part);
            long endpointCount = share(stats.getCount(), parts, index);
            cell.endpoint(endpoint).add(endpointCount, share(stats.getClientErrors(), parts, index),
                    share(stats.getServerErrors(), parts, index),
                    proportional(stats.getDurationSumMs(), endpointBefore, endpointCount, stats.getCount()));
        });
        return cell;
    }

    private static List<Long> latencyOf(MetricsBucket data) {
        List<Long> latency = new ArrayList<>();
        for (long count : data.getLatencyCounts()) latency.add(count);
        return latency;
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
                data.p95Ms(), spanMinutes, latencyOf(data), false);
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
