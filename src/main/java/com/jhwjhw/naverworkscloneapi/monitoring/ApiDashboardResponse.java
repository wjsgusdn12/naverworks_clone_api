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
        List<EndpointStat> topEndpoints,
        List<EndpointStat> slowEndpoints) {

    // 기간 합계. errorRate는 5xx(서버 오류) 비율(%), p95Ms가 -1이면 5초 초과.
    public record Summary(long requests, long clientErrors, long serverErrors, double errorRate, long avgMs,
            long p95Ms) {
    }

    // 추이 한 점(그 구간의 요청·오류 수와 평균·p95 응답 시간). 요청이 없으면 avgMs·p95Ms는 0.
    public record TrendPoint(Instant startAt, long requests, long errors, long avgMs, long p95Ms) {
    }

    // 오늘 기준 API 순위 한 줄. hourlyRequests·hourlyAvgMs는 오늘 0~23시 1시간 단위 값(작은 추이선용).
    public record EndpointStat(String endpoint, long requests, long errors, long avgMs, List<Long> hourlyRequests,
            List<Long> hourlyAvgMs) {
    }

    // current: 지금 이 응답을 만든 서버
    public record Server(String instanceId, Instant startedAt, long uptimeSeconds, long heapUsedMb, long heapMaxMb,
            int realtimeConnections, boolean current) {
    }
}
