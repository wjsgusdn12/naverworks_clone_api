package com.jhwjhw.naverworkscloneapi.monitoring;

import com.jhwjhw.naverworkscloneapi.presence.PresenceService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 10초마다 "이 서버 살아 있음 + 메모리·실시간 연결 수"를 집계판에 보고(출석 체크).
// 보고가 30초 끊기면 대시보드 서버 목록에서 자동으로 빠짐.
@Component
@Slf4j
public class ServerStatusReporter {
    private static final long MB = 1024 * 1024;

    private final ApiMetricsStore store;
    private final PresenceService presenceService;
    private final String instanceId;
    private final Instant startedAt = Instant.now();

    public ServerStatusReporter(ApiMetricsStore store, PresenceService presenceService,
            @Value("${app.monitoring.instance-id:}") String configuredInstanceId) {
        this.store = store;
        this.presenceService = presenceService;
        // 배포 때는 APP_INSTANCE_ID(api-1, api-2)로 이름을 주고, 없으면 컴퓨터 이름 + 임의 4글자.
        this.instanceId = StringUtils.hasText(configuredInstanceId) ? configuredInstanceId
                : hostName() + "-" + UUID.randomUUID().toString().substring(0, 4);
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "server";
        }
    }

    public String instanceId() {
        return instanceId;
    }

    @Scheduled(fixedRate = 10_000)
    public void report() {
        try {
            store.reportServer(current());
        } catch (RuntimeException exception) {
            // 저장소가 잠깐 끊겨도 서버가 멈추지 않게 기록만 남김.
            log.warn("서버 상태를 보고하지 못했습니다. reason={}", exception.getMessage());
        }
    }

    public ApiMetricsStore.ServerStatus current() {
        Runtime runtime = Runtime.getRuntime();
        return new ApiMetricsStore.ServerStatus(instanceId, startedAt, Instant.now(),
                (runtime.totalMemory() - runtime.freeMemory()) / MB, runtime.maxMemory() / MB,
                presenceService.connectionCount());
    }
}
