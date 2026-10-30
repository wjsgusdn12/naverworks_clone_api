package com.jhwjhw.naverworkscloneapi.presence;

import com.jhwjhw.naverworkscloneapi.identity.domain.MemberStatusType;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

// 구성원 접속 상태(온라인·오프라인) 관리.
// 진짜 기준은 공용 출석부(PresenceStore)이고, 이 서버는 "자기에게 붙은 연결" 목록만 따로 들고 있다가
// 20초마다 출석부의 만료 시각을 연장함. 서버가 꺼지면 연장이 멈춰 60초 안에 자동으로 오프라인 처리됨.
@Service
@Slf4j
public class PresenceService {
    private final PresenceStore store;
    private final PresenceBroadcaster broadcaster;
    // 상태 설정(statusType)을 알림에 함께 싣기 위해 소속 정보를 조회.
    private final MembershipRepository membershipRepository;
    // 테스트에서 시간을 앞으로 돌려 만료를 흉내 낼 수 있게 시계를 주입.
    private final Clock clock;
    // 이 서버에 붙은 연결 ID → 구성원 ID
    private final ConcurrentHashMap<String, Long> membershipBySessionId = new ConcurrentHashMap<>();

    @Autowired
    public PresenceService(PresenceStore store, PresenceBroadcaster broadcaster,
            MembershipRepository membershipRepository) {
        this(store, broadcaster, membershipRepository, Clock.systemUTC());
    }

    PresenceService(PresenceStore store, PresenceBroadcaster broadcaster, MembershipRepository membershipRepository,
            Clock clock) {
        this.store = store;
        this.broadcaster = broadcaster;
        this.membershipRepository = membershipRepository;
        this.clock = clock;
    }

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
        // 모든 서버를 통틀어 첫 연결일 때만 "온라인" 알림(탭을 여러 개 열어도 한 번).
        if (store.addSession(membershipId, sessionId, clock.instant())) {
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
        // 다른 탭·다른 서버에 연결이 남아 있으면 오프라인이 아님.
        if (store.removeSession(membershipId, sessionId, clock.instant())) {
            publish(membershipId, false);
        }
    }

    // 이 서버 연결들의 만료 시각을 연장(출석 연장). 만료(60초)보다 충분히 짧은 간격으로 실행.
    @Scheduled(fixedRate = 20_000)
    public void refreshLocalSessions() {
        Map<Long, Set<String>> sessionIdsByMembershipId = new HashMap<>();
        membershipBySessionId.forEach((sessionId, membershipId) ->
                sessionIdsByMembershipId.computeIfAbsent(membershipId, key -> new HashSet<>()).add(sessionId));
        try {
            store.refresh(sessionIdsByMembershipId, clock.instant());
        } catch (RuntimeException exception) {
            log.warn("접속 상태 연장에 실패했습니다. reason={}", exception.getMessage());
        }
    }

    // 만료된 연결 정리. 꺼진 서버에 붙어 있던 사람들이 여기서 오프라인으로 바뀜(어느 서버가 해도 한 번만 알림).
    @Scheduled(fixedRate = 15_000)
    public void sweepExpiredSessions() {
        try {
            store.sweepExpired(clock.instant()).forEach(membershipId -> publish(membershipId, false));
        } catch (RuntimeException exception) {
            log.warn("만료된 접속 정리에 실패했습니다. reason={}", exception.getMessage());
        }
    }

    // 이 서버에 붙어 있는 실시간(WebSocket) 연결 수. API 대시보드의 서버 상태에 표시.
    public int connectionCount() {
        return membershipBySessionId.size();
    }

    // 주소록·채팅 구성원 목록의 온라인 표시. 모든 서버를 합친 출석부 기준.
    public boolean isOnline(Long membershipId) {
        return store.isOnline(membershipId, clock.instant());
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
        broadcaster.broadcast(
                new PresenceRealtimeEvent("PRESENCE_CHANGED", membershipId, online, status, statusType, statusUntil));
        log.debug("Presence changed. membershipId={}, online={}, statusType={}", membershipId, online, statusType);
    }
}
