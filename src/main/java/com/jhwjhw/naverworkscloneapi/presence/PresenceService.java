package com.jhwjhw.naverworkscloneapi.presence;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Service
@RequiredArgsConstructor
@Slf4j
// 서버 인스턴스 안에서 활성 WebSocket 세션을 추적하는 1차 Presence 구현.
public class PresenceService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ConcurrentHashMap<String, Long> membershipBySessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> sessionIdsByMembershipId = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(event.getMessage(), StompHeaderAccessor.class);
        if (accessor == null || !(accessor.getUser() instanceof JwtAuthenticationToken authentication)) {
            return;
        }

        Long membershipId = authentication.getToken().getClaim("membershipId");
        String sessionId = accessor.getSessionId();
        if (membershipId == null || sessionId == null || membershipBySessionId.putIfAbsent(sessionId, membershipId) != null) {
            return;
        }

        Set<String> sessionIds = sessionIdsByMembershipId.computeIfAbsent(membershipId,
                ignored -> ConcurrentHashMap.newKeySet());
        boolean wasOffline = sessionIds.isEmpty();
        sessionIds.add(sessionId);
        if (wasOffline) {
            publish(membershipId, true);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Long membershipId = membershipBySessionId.remove(sessionId);
        if (membershipId == null) {
            return;
        }

        Set<String> sessionIds = sessionIdsByMembershipId.get(membershipId);
        if (sessionIds == null) {
            return;
        }
        sessionIds.remove(sessionId);
        if (!sessionIds.isEmpty()) {
            return;
        }
        sessionIdsByMembershipId.remove(membershipId);
        publish(membershipId, false);
    }

    // 주소록 조회는 연결 중인 세션이 하나라도 있으면 온라인으로 표시.
    public boolean isOnline(Long membershipId) {
        Set<String> sessionIds = sessionIdsByMembershipId.get(membershipId);
        return sessionIds != null && !sessionIds.isEmpty();
    }

    private void publish(Long membershipId, boolean online) {
        messagingTemplate.convertAndSend("/topic/presence",
                new PresenceRealtimeEvent("PRESENCE_CHANGED", membershipId, online));
        log.debug("Presence changed. membershipId={}, online={}", membershipId, online);
    }
}
