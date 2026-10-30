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

    @Getter
    public static final class EndpointStats {
        private long count;
        private long errors;
        private long durationSumMs;

        public void add(long count, long errors, long durationSumMs) {
            this.count += count;
            this.errors += errors;
            this.durationSumMs += durationSumMs;
        }
    }

    // 요청 한 건 기록
    public void record(String endpoint, int status, long durationMs) {
        boolean error = status >= 400;
        addTotals(1, status >= 400 && status < 500 ? 1 : 0, status >= 500 ? 1 : 0, durationMs);
        latencyCounts[latencyIndex(durationMs)] += 1;
        endpoints.computeIfAbsent(endpoint, key -> new EndpointStats()).add(1, error ? 1 : 0, durationMs);
    }

    // 다른 칸(다른 서버·다른 시간)의 값을 이 칸에 더함
    public void add(MetricsBucket other) {
        addTotals(other.count, other.clientErrors, other.serverErrors, other.durationSumMs);
        for (int i = 0; i < latencyCounts.length; i++) latencyCounts[i] += other.latencyCounts[i];
        other.endpoints.forEach((endpoint, stats) -> endpoints.computeIfAbsent(endpoint, key -> new EndpointStats())
                .add(stats.count, stats.errors, stats.durationSumMs));
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

    // 전체 요청의 95%가 이 시간 안에 끝났다는 값(구간의 위쪽 경계). 5초 초과 구간이면 -1.
    public long p95Ms() {
        if (count == 0) return 0;
        long target = (long) Math.ceil(count * 0.95);
        long cumulative = 0;
        for (int i = 0; i < latencyCounts.length; i++) {
            cumulative += latencyCounts[i];
            if (cumulative >= target) return i < LATENCY_BOUNDS_MS.length ? LATENCY_BOUNDS_MS[i] : -1;
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
