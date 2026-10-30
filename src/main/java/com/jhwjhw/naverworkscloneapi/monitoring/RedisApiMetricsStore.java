package com.jhwjhw.naverworkscloneapi.monitoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// Redis 버전 서버 상태판(배포용). 서버 1·2가 같은 해시에 자기 상태를 쓰므로 어느 서버에서 읽어도 전체 목록이 보임.
// (요청 집계는 재시작에도 남도록 DB에 저장)
//
// 저장 모양
// - apiMetrics:servers  해시. 서버 이름 → "시작시각|보고시각|사용메모리|최대메모리|실시간연결수"
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RedisApiMetricsStore implements ApiMetricsStore {
    private static final String SERVERS_KEY = "apiMetrics:servers";

    private final StringRedisTemplate redis;

    @Override
    public void reportServer(ServerStatus status) {
        redis.opsForHash().put(SERVERS_KEY, status.instanceId(),
                status.startedAt().toEpochMilli() + "|" + status.reportedAt().toEpochMilli() + "|"
                        + status.heapUsedMb() + "|" + status.heapMaxMb() + "|" + status.realtimeConnections());
    }

    @Override
    public List<ServerStatus> servers(Instant now) {
        List<ServerStatus> alive = new ArrayList<>();
        redis.opsForHash().entries(SERVERS_KEY).forEach((rawId, rawValue) -> {
            String[] parts = String.valueOf(rawValue).split("\\|");
            if (parts.length != 5) return;
            ServerStatus status = new ServerStatus(String.valueOf(rawId), Instant.ofEpochMilli(Long.parseLong(parts[0])),
                    Instant.ofEpochMilli(Long.parseLong(parts[1])), Long.parseLong(parts[2]), Long.parseLong(parts[3]),
                    Integer.parseInt(parts[4]));
            if (status.reportedAt().isBefore(now.minus(SERVER_TIMEOUT))) {
                // 30초 넘게 보고가 없으면 꺼진 서버로 보고 목록에서 지움.
                redis.opsForHash().delete(SERVERS_KEY, rawId);
            } else {
                alive.add(status);
            }
        });
        alive.sort(Comparator.comparing(ServerStatus::instanceId));
        return alive;
    }

    @Override
    public String mode() {
        return "SHARED";
    }
}
