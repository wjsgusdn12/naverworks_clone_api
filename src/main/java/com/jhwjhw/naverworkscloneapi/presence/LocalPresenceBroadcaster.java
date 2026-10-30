package com.jhwjhw.naverworkscloneapi.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

// 이 서버 버전 안내 방송(로컬 개발용): 이 서버에 붙은 브라우저들에게 바로 전달.
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalPresenceBroadcaster implements PresenceBroadcaster {
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void broadcast(PresenceRealtimeEvent event) {
        messagingTemplate.convertAndSend(TOPIC, event);
    }
}
