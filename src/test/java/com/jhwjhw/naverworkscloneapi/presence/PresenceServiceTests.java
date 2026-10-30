package com.jhwjhw.naverworkscloneapi.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jhwjhw.naverworkscloneapi.identity.domain.MemberStatusType;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

// 서버 두 대가 같은 출석부를 쓰는 상황을 흉내 내 접속 상태 공유·만료(서버 꺼짐)·연장을 검증.
// (Redis 버전도 같은 규칙으로 동작하지만, Redis가 필요한 실제 연동은 이 테스트 범위 밖)
class PresenceServiceTests {
    // 테스트에서 시간을 마음대로 앞으로 돌리는 시계.
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-30T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final List<PresenceRealtimeEvent> events = new ArrayList<>();
    private PresenceService server1;
    private PresenceService server2;

    @BeforeEach
    void setUp() {
        InMemoryPresenceStore sharedStore = new InMemoryPresenceStore();
        MembershipRepository memberships = mock(MembershipRepository.class);
        when(memberships.findById(any())).thenReturn(Optional.empty());
        // 두 서버가 같은 출석부·같은 방송을 씀.
        server1 = new PresenceService(sharedStore, events::add, memberships, clock);
        server2 = new PresenceService(sharedStore, events::add, memberships, clock);
    }

    private static SessionConnectEvent connect(Long membershipId, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        JwtAuthenticationToken user = new JwtAuthenticationToken(Jwt.withTokenValue("token").header("alg", "none")
                .claim("membershipId", membershipId).build());
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionConnectEvent(PresenceServiceTests.class, message, user);
    }

    private static SessionDisconnectEvent disconnect(String sessionId) {
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0],
                StompHeaderAccessor.create(StompCommand.DISCONNECT).getMessageHeaders());
        return new SessionDisconnectEvent(PresenceServiceTests.class, message, sessionId, CloseStatus.NORMAL);
    }

    // 서버 1·2에 같은 사람의 탭이 하나씩: 온라인 알림은 한 번, 둘 다 닫혀야 오프라인 한 번.
    @Test
    void onlineAcrossServersUntilLastConnectionCloses() {
        server1.handleSessionConnect(connect(7L, "tab-a"));
        server2.handleSessionConnect(connect(7L, "tab-b"));
        assertThat(events).extracting(PresenceRealtimeEvent::online).containsExactly(true);
        assertThat(server1.isOnline(7L)).isTrue();
        assertThat(server2.isOnline(7L)).isTrue();

        server1.handleSessionDisconnect(disconnect("tab-a"));
        assertThat(events).hasSize(1);
        assertThat(server2.isOnline(7L)).isTrue();

        server2.handleSessionDisconnect(disconnect("tab-b"));
        assertThat(events).extracting(PresenceRealtimeEvent::online).containsExactly(true, false);
        assertThat(server1.isOnline(7L)).isFalse();
    }

    // 서버 1이 갑자기 꺼져 연장을 못 하면, 60초 뒤 다른 서버의 정리 작업이 오프라인으로 바꿈.
    @Test
    void connectionsOfCrashedServerExpire() {
        server1.handleSessionConnect(connect(7L, "tab-a"));
        clock.advance(Duration.ofSeconds(30));
        server2.sweepExpiredSessions();
        assertThat(server2.isOnline(7L)).isTrue();

        clock.advance(Duration.ofSeconds(31));
        assertThat(server2.isOnline(7L)).isFalse();
        server2.sweepExpiredSessions();
        assertThat(events).extracting(PresenceRealtimeEvent::online).containsExactly(true, false);
        // 정리는 한 번만: 다시 돌려도 오프라인 알림이 또 나가지 않음.
        server1.sweepExpiredSessions();
        assertThat(events).hasSize(2);
    }

    // 서버가 살아서 20초마다 연장하면 아무리 오래 지나도 온라인 유지.
    @Test
    void refreshKeepsLiveConnectionsOnline() {
        server1.handleSessionConnect(connect(7L, "tab-a"));
        for (int i = 0; i < 10; i++) {
            clock.advance(Duration.ofSeconds(20));
            server1.refreshLocalSessions();
            server2.sweepExpiredSessions();
        }
        assertThat(server2.isOnline(7L)).isTrue();
        assertThat(events).hasSize(1);
        assertThat(server1.connectionCount()).isEqualTo(1);
        assertThat(server2.connectionCount()).isZero();
    }

    // 새로고침: 새 연결(tab-new)이 먼저 붙고 옛 연결(tab-old)의 종료 알림이 늦게 와도 계속 온라인.
    // 같은 종료 알림이 두 번 와도 아무 일 없음.
    @Test
    void lateDisconnectOfOldConnectionKeepsOnline() {
        server1.handleSessionConnect(connect(7L, "tab-old"));
        server2.handleSessionConnect(connect(7L, "tab-new"));

        server1.handleSessionDisconnect(disconnect("tab-old"));
        server1.handleSessionDisconnect(disconnect("tab-old"));
        assertThat(server2.isOnline(7L)).isTrue();
        assertThat(events).extracting(PresenceRealtimeEvent::online).containsExactly(true);
    }

    // 만료 정리로 이미 오프라인 알림이 나간 연결의 종료 알림이 늦게 와도 오프라인 알림이 또 나가지 않음.
    @Test
    void lateDisconnectAfterExpiryDoesNotRepeatOffline() {
        server1.handleSessionConnect(connect(7L, "tab-a"));
        clock.advance(Duration.ofSeconds(61));
        server2.sweepExpiredSessions();
        assertThat(events).extracting(PresenceRealtimeEvent::online).containsExactly(true, false);

        server1.handleSessionDisconnect(disconnect("tab-a"));
        assertThat(events).hasSize(2);
        assertThat(server1.isOnline(7L)).isFalse();
    }

    // 서버 사이 방송 글자를 보내고 받았을 때 내용이 그대로인지.
    @Test
    void broadcastMessageRoundTrip() {
        PresenceRealtimeEvent event = new PresenceRealtimeEvent("PRESENCE_CHANGED", 7L, true, "ONLINE",
                MemberStatusType.BUSY, Instant.parse("2026-10-30T09:00:00Z"));
        assertThat(RedisPresenceBroadcaster.decode(RedisPresenceBroadcaster.encode(event))).isEqualTo(event);
        PresenceRealtimeEvent empty = new PresenceRealtimeEvent("PRESENCE_CHANGED", 8L, false, "OFFLINE", null, null);
        assertThat(RedisPresenceBroadcaster.decode(RedisPresenceBroadcaster.encode(empty))).isEqualTo(empty);
    }
}
