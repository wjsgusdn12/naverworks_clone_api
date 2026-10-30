package com.jhwjhw.naverworkscloneapi.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// API 집계를 모아 두는 "공용 집계판".
// - Redis 버전: 서버 여러 대가 같은 곳에 더하므로 어느 서버에서 읽어도 합계가 같음(배포용).
// - 메모리 버전: 이 서버 안에만 모음(로컬 개발용). app.redis.enabled 값으로 자동 선택.
public interface ApiMetricsStore {
    // 5분 칸은 25시간 지나면 지움(대시보드는 최근 24시간만 봄).
    Duration RETENTION = Duration.ofHours(25);
    // 서버 상태 보고가 이 시간 동안 없으면 꺼진 서버로 보고 목록에서 뺌.
    Duration SERVER_TIMEOUT = Duration.ofSeconds(30);

    // 각 서버가 모아 둔 칸들을 집계판에 더함(key: 5분 칸 번호).
    void merge(Map<Long, MetricsBucket> deltas);

    // fromBucket~toBucket 범위의 칸을 읽음. 기록이 없는 칸은 빠짐.
    Map<Long, MetricsBucket> read(long fromBucket, long toBucket);

    // 서버 한 대의 "살아 있음 + 상태" 보고
    void reportServer(ServerStatus status);

    // 최근 SERVER_TIMEOUT 안에 보고한 서버 목록
    List<ServerStatus> servers(Instant now);

    // 화면 안내용: SHARED(서버 통합 집계) 또는 LOCAL(이 서버만)
    String mode();

    // 서버 한 대의 상태. 메모리는 MB 단위.
    record ServerStatus(String instanceId, Instant startedAt, Instant reportedAt, long heapUsedMb, long heapMaxMb,
            int realtimeConnections) {
    }
}
