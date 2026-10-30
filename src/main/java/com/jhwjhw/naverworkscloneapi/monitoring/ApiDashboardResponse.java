package com.jhwjhw.naverworkscloneapi.monitoring;

import java.time.Instant;
import java.util.List;

// 홈 화면 API 대시보드 응답. 화면이 고른 기간(range) 하나만 담음. 기간은 달력 기준(zone 시간대).
// mode: SHARED(서버 목록 통합, Redis) / LOCAL(서버 목록은 이 서버만, 로컬 개발). 요청 집계는 항상 DB 기준.
// trend: HOUR 이번 시간 1분×60, DAY 오늘 5분×288, WEEK 이번 주(일~토) 1시간×168, MONTH 이번 달 1시간×말일×24
public record ApiDashboardResponse(Instant generatedAt, String zone, String mode, String database,
        List<Server> servers, String range, Summary summary, List<TrendPoint> trend, Ranking ranking) {

    // 기간 합계. errorRate는 5xx(서버 오류) 비율(%), p95Ms가 -1이면 5초 초과.
    public record Summary(long requests, long clientErrors, long serverErrors, double errorRate, long avgMs,
            long p95Ms) {
    }

    // 추이 한 점(그 구간의 요청·오류 수와 평균·p95 응답 시간). 요청이 없으면 avgMs·p95Ms는 0.
    // spanMinutes: 이 점이 담은 시간(분). 보통은 그래프 한 칸 길이(1·5·60분)이고,
    // 잘게 쪼갠 기록이 없는 구간을 큰 단위 기록(1시간·5분 합계)으로 채우면 그 길이가 되며,
    // 그 묶음에 포함돼 따로 값이 없는 나머지 칸은 0(화면은 빈칸으로 그림).
    // latency: 응답 시간 구간별 요청 수(50·100·200·300·500ms·1·2·5초 이하, 5초 초과 9칸, 히트맵용).
    public record TrendPoint(Instant startAt, long requests, long errors, long avgMs, long p95Ms, int spanMinutes,
            List<Long> latency) {
    }

    // 기간별 API 순위. bins는 작은 추이선 칸의 시작 시각(이번 시간 1분×60, 오늘 5분×288, 이번 주 1일×7, 이번 달 1시간).
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
