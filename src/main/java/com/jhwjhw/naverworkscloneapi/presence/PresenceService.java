package com.jhwjhw.naverworkscloneapi.presence;

import com.jhwjhw.naverworkscloneapi.identity.domain.MemberStatusType;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import java.time.Instant;
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
    // 상태 설정(statusType)을 이벤트에 함께 싣기 위해 소속 정보를 조회.
    private final MembershipRepository membershipRepository;
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

    // 이 서버에 붙어 있는 실시간(WebSocket) 연결 수. API 대시보드의 서버 상태에 표시.
    public int connectionCount() {
        return membershipBySessionId.size();
    }

    // 주소록 조회는 연결 중인 세션이 하나라도 있으면 온라인으로 표시.
    public boolean isOnline(Long membershipId) {
        Set<String> sessionIds = sessionIdsByMembershipId.get(membershipId);
        return sessionIds != null && !sessionIds.isEmpty();
    }

    // 상태 설정이 바뀌었을 때 현재 상태를 다시 알림.
    public void publishCurrent(Long membershipId) {
        publish(membershipId, isOnline(membershipId));
    }

    private void publish(Long membershipId, boolean online) {
        String status = online ? "ONLINE" : "OFFLINE";
        // 상태 설정(바쁨·휴가 등)은 소속 정보에서 읽어 함께 보냄. 자동 해제 시각이 지났으면 null.
        Membership membership = membershipRepository.findById(membershipId).orElse(null);
        MemberStatusType statusType = membership == null ? null : membership.currentStatusType();
        Instant statusUntil = membership == null ? null : membership.currentStatusUntil();
        messagingTemplate.convertAndSend("/topic/presence",
                new PresenceRealtimeEvent("PRESENCE_CHANGED", membershipId, online, status, statusType, statusUntil));
        log.debug("Presence changed. membershipId={}, online={}, statusType={}", membershipId, online, statusType);
    }
}
