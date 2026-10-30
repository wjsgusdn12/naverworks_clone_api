package com.jhwjhw.naverworkscloneapi.monitoring;

import java.time.Instant;
import java.util.List;

// 홈 화면 API 대시보드 응답.
// mode: SHARED(Redis로 모든 서버 합산) / LOCAL(이 서버만, 로컬 개발)
// trend: 최근 24시간 1시간 단위 24점, recentTrend: 최근 1시간 5분 단위 12점
public record ApiDashboardResponse(Instant generatedAt, String mode, String database, List<Server> servers,
        Summary lastHour, Summary lastDay, List<TrendPoint> trend, List<TrendPoint> recentTrend,
        List<EndpointStat> topEndpoints, List<EndpointStat> slowEndpoints) {

    // 기간 합계. errorRate는 5xx(서버 오류) 비율(%), p95Ms가 -1이면 5초 초과.
    public record Summary(long requests, long clientErrors, long serverErrors, double errorRate, long avgMs,
            long p95Ms) {
    }

    // 추이 한 점(그 구간의 요청·오류 수와 평균·p95 응답 시간). 요청이 없으면 avgMs·p95Ms는 0.
    public record TrendPoint(Instant startAt, long requests, long errors, long avgMs, long p95Ms) {
    }

    // hourlyRequests·hourlyAvgMs: 최근 24시간 1시간 단위 값(작은 추이선용)
    public record EndpointStat(String endpoint, long requests, long errors, long avgMs, List<Long> hourlyRequests,
            List<Long> hourlyAvgMs) {
    }

    // current: 지금 이 응답을 만든 서버
    public record Server(String instanceId, Instant startedAt, long uptimeSeconds, long heapUsedMb, long heapMaxMb,
            int realtimeConnections, boolean current) {
    }
}
