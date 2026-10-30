package com.jhwjhw.naverworkscloneapi.monitoring;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

// 5분 동안의 API 요청 집계 한 칸. 서버마다 따로 모은 칸을 add로 더해 하나로 합칠 수 있게 설계.
// 응답 시간은 평균만으로는 느린 요청이 묻히므로 구간별 개수(히스토그램)로도 모아 p95를 계산.
// 이 객체는 스레드 안전하지 않으므로 쓰는 쪽에서 동기화함.
@Getter
public class MetricsBucket {
    // 응답 시간 구간 경계(ms). 마지막 칸은 5초 초과.
    public static final long[] LATENCY_BOUNDS_MS = { 50, 100, 200, 300, 500, 1000, 2000, 5000 };

    private long count;
    // 4xx(요청 쪽 잘못), 5xx(서버 쪽 잘못) 개수
    private long clientErrors;
    private long serverErrors;
    private long durationSumMs;
    private final long[] latencyCounts = new long[LATENCY_BOUNDS_MS.length + 1];
    // 엔드포인트("GET /api/v1/chatRoom" 형태)별 집계
    private final Map<String, EndpointStats> endpoints = new HashMap<>();

    // API 하나의 집계. 오류는 4xx(요청 쪽)·5xx(서버 쪽)로 나눠 셈.
    @Getter
    public static final class EndpointStats {
        private long count;
        private long clientErrors;
        private long serverErrors;
        private long durationSumMs;

        public void add(long count, long clientErrors, long serverErrors, long durationSumMs) {
            this.count += count;
            this.clientErrors += clientErrors;
            this.serverErrors += serverErrors;
            this.durationSumMs += durationSumMs;
        }

        // 4xx + 5xx
        public long getErrors() {
            return clientErrors + serverErrors;
        }
    }

    // 요청 한 건 기록
    public void record(String endpoint, int status, long durationMs) {
        long clientError = status >= 400 && status < 500 ? 1 : 0;
        long serverError = status >= 500 ? 1 : 0;
        addTotals(1, clientError, serverError, durationMs);
        latencyCounts[latencyIndex(durationMs)] += 1;
        endpoints.computeIfAbsent(endpoint, key -> new EndpointStats()).add(1, clientError, serverError, durationMs);
    }

    // 다른 칸(다른 서버·다른 시간)의 값을 이 칸에 더함
    public void add(MetricsBucket other) {
        addTotals(other.count, other.clientErrors, other.serverErrors, other.durationSumMs);
        for (int i = 0; i < latencyCounts.length; i++) latencyCounts[i] += other.latencyCounts[i];
        other.endpoints.forEach((endpoint, stats) -> endpoints.computeIfAbsent(endpoint, key -> new EndpointStats())
                .add(stats.count, stats.clientErrors, stats.serverErrors, stats.durationSumMs));
    }

    // 저장소에서 읽어 온 값을 채울 때 사용
    public void addTotals(long count, long clientErrors, long serverErrors, long durationSumMs) {
        this.count += count;
        this.clientErrors += clientErrors;
        this.serverErrors += serverErrors;
        this.durationSumMs += durationSumMs;
    }

    public void addLatency(int index, long count) {
        latencyCounts[index] += count;
    }

    public EndpointStats endpoint(String endpoint) {
        return endpoints.computeIfAbsent(endpoint, key -> new EndpointStats());
    }

    public boolean isEmpty() {
        return count == 0;
    }

    // 전체 요청의 95%가 이 시간 안에 끝났다는 값(추정). 5초 초과 구간이면 -1.
    // 95번째 요청이 든 구간 안에서 몇 번째인지로 비례 계산(선형 보간, 프로메테우스와 같은 방식).
    // 예: 500~1000ms 구간에 10건이 있고 그중 3번째가 95번째면 500 + 500 × 3/10 = 650ms.
    // (구간의 위쪽 끝만 쓰면 500~1000ms 사이 요청이 모두 1000ms로 보임)
    public long p95Ms() {
        if (count == 0) return 0;
        long target = (long) Math.ceil(count * 0.95);
        long cumulative = 0;
        for (int i = 0; i < latencyCounts.length; i++) {
            long before = cumulative;
            cumulative += latencyCounts[i];
            if (cumulative < target) continue;
            if (i >= LATENCY_BOUNDS_MS.length) return -1;
            long lower = i == 0 ? 0 : LATENCY_BOUNDS_MS[i - 1];
            long upper = LATENCY_BOUNDS_MS[i];
            return Math.round(lower + (upper - lower) * (double) (target - before) / latencyCounts[i]);
        }
        return -1;
    }

    static int latencyIndex(long durationMs) {
        for (int i = 0; i < LATENCY_BOUNDS_MS.length; i++) {
            if (durationMs <= LATENCY_BOUNDS_MS[i]) return i;
        }
        return LATENCY_BOUNDS_MS.length;
    }
}
