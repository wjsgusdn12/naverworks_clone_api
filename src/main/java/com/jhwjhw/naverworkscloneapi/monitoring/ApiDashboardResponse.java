package com.jhwjhw.naverworkscloneapi.monitoring;

import java.time.Instant;
import java.util.List;

// 홈 화면 API 대시보드 응답. 기간은 달력 기준(zone 시간대).
// mode: SHARED(Redis로 모든 서버 합산) / LOCAL(이 서버만, 로컬 개발)
// hourTrend: 이번 시간 ??:00~??:59 5분 단위 12점
// dayTrend: 오늘 00:00~23:59 1시간 단위 24점
// weekTrend: 이번 주 일요일 00시~토요일 23시 1시간 단위 168점(DB의 1시간 요약)
// monthTrend: 이번 달 1일~말일 1일 단위(DB의 1시간 요약을 하루씩 묶음)
public record ApiDashboardResponse(Instant generatedAt, String zone, String mode, String database,
        List<Server> servers, Summary thisHour, Summary today, Summary thisWeek, Summary thisMonth,
        List<TrendPoint> hourTrend, List<TrendPoint> dayTrend, List<TrendPoint> weekTrend, List<TrendPoint> monthTrend,
        Ranking hourRanking, Ranking dayRanking, Ranking weekRanking, Ranking monthRanking) {

    // 기간 합계. errorRate는 5xx(서버 오류) 비율(%), p95Ms가 -1이면 5초 초과.
    public record Summary(long requests, long clientErrors, long serverErrors, double errorRate, long avgMs,
            long p95Ms) {
    }

    // 추이 한 점(그 구간의 요청·오류 수와 평균·p95 응답 시간). 요청이 없으면 avgMs·p95Ms는 0.
    // spanMinutes: 이 점이 담은 시간(분). 보통은 그래프 한 칸 길이(1·5·60분)이고,
    // 잘게 쪼갠 기록이 없는 구간을 큰 단위 기록(1시간·5분 합계)으로 채우면 그 길이가 되며,
    // 그 묶음에 포함돼 따로 값이 없는 나머지 칸은 0(화면은 빈칸으로 그림).
    public record TrendPoint(Instant startAt, long requests, long errors, long avgMs, long p95Ms, int spanMinutes) {
    }

    // 기간별 API 순위. bins는 작은 추이선 칸의 시작 시각(이번 시간 5분×12, 오늘 1시간×24, 이번 주 1일×7, 이번 달 1일×말일).
    public record Ranking(List<Instant> bins, List<EndpointStat> top, List<EndpointStat> slow) {
    }

    // 순위 한 줄. trendRequests·trendAvgMs는 bins 칸마다의 호출 수·평균 응답 시간(작은 추이선용).
    public record EndpointStat(String endpoint, long requests, long errors, long avgMs, List<Long> trendRequests,
            List<Long> trendAvgMs) {
    }

    // current: 지금 이 응답을 만든 서버
    public record Server(String instanceId, Instant startedAt, long uptimeSeconds, long heapUsedMb, long heapMaxMb,
            int realtimeConnections, boolean current) {
    }
}
