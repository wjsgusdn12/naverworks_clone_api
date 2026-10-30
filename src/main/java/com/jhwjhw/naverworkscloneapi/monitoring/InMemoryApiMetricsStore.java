package com.jhwjhw.naverworkscloneapi.monitoring;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 메모리 버전 서버 상태판(로컬 개발용). 이 서버 상태만 보임(요청 집계는 DB에 저장).
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryApiMetricsStore implements ApiMetricsStore {
    private final Map<String, ServerStatus> servers = new ConcurrentHashMap<>();

    @Override
    public void reportServer(ServerStatus status) {
        servers.put(status.instanceId(), status);
    }

    @Override
    public List<ServerStatus> servers(Instant now) {
        servers.values().removeIf(status -> status.reportedAt().isBefore(now.minus(SERVER_TIMEOUT)));
        return servers.values().stream().sorted(Comparator.comparing(ServerStatus::instanceId)).toList();
    }

    @Override
    public String mode() {
        return "LOCAL";
    }
}
