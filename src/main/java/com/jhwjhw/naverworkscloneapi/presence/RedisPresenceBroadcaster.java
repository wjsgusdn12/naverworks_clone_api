package com.jhwjhw.naverworkscloneapi.presence;

import com.jhwjhw.naverworkscloneapi.identity.domain.MemberStatusType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

// Redis 버전 안내 방송(배포용).
// 1) 상태가 바뀐 서버가 Redis 방송 채널에 짧은 글자로 보냄(publish)
// 2) 이 채널을 듣고 있던 모든 서버(보낸 서버 포함)가 받아서(onMessage) 자기 브라우저들에게 전달
// 그래서 서버 1에 붙은 사람의 접속·해제 소식이 서버 2에 붙은 사람 화면에도 바로 보임.
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RedisPresenceBroadcaster implements PresenceBroadcaster, MessageListener {
    public static final String CHANNEL = "realtime:presence";

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void broadcast(PresenceRealtimeEvent event) {
        redis.convertAndSend(CHANNEL, encode(event));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            messagingTemplate.convertAndSend(TOPIC, decode(new String(message.getBody(), StandardCharsets.UTF_8)));
        } catch (RuntimeException exception) {
            log.warn("접속 상태 방송을 해석하지 못했습니다. reason={}", exception.getMessage());
        }
    }

    // "구성원ID|online|상태|상태설정|해제시각(ms)" 모양의 짧은 글자. 값이 없으면 빈칸.
    static String encode(PresenceRealtimeEvent event) {
        return event.membershipId() + "|" + event.online() + "|" + event.status() + "|"
                + (event.statusType() == null ? "" : event.statusType().name()) + "|"
                + (event.statusUntil() == null ? "" : event.statusUntil().toEpochMilli());
    }

    static PresenceRealtimeEvent decode(String text) {
        String[] parts = text.split("\\|", -1);
        return new PresenceRealtimeEvent("PRESENCE_CHANGED", Long.valueOf(parts[0]), Boolean.parseBoolean(parts[1]),
                parts[2], parts[3].isEmpty() ? null : MemberStatusType.valueOf(parts[3]),
                parts[4].isEmpty() ? null : Instant.ofEpochMilli(Long.parseLong(parts[4])));
    }
}
