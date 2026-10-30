package com.jhwjhw.naverworkscloneapi.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// 서버 상태판: 서버마다 10초마다 "살아 있음 + 상태"를 보고하고, 대시보드가 서버 목록을 읽음.
// (요청 집계는 재시작에도 남도록 DB에 저장 → ApiMetricBucketRepository·ApiMetricHourlyRepository)
// - Redis 버전: 서버 여러 대의 보고를 한곳에 모아 어느 서버에서 읽어도 전체 목록이 보임(배포용).
// - 메모리 버전: 이 서버만 보임(로컬 개발용). app.redis.enabled 값으로 자동 선택.
public interface ApiMetricsStore {
    // 서버 상태 보고가 이 시간 동안 없으면 꺼진 서버로 보고 목록에서 뺌.
    Duration SERVER_TIMEOUT = Duration.ofSeconds(30);

    // 서버 한 대의 "살아 있음 + 상태" 보고
    void reportServer(ServerStatus status);

    // 최근 SERVER_TIMEOUT 안에 보고한 서버 목록
    List<ServerStatus> servers(Instant now);

    // 화면 안내용: SHARED(서버 목록 통합) 또는 LOCAL(서버 목록은 이 서버만)
    String mode();

    // 서버 한 대의 상태. 메모리는 MB 단위.
    record ServerStatus(String instanceId, Instant startedAt, Instant reportedAt, long heapUsedMb, long heapMaxMb,
            int realtimeConnections) {
    }
}
