package com.jhwjhw.naverworkscloneapi.presence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 메모리 버전 출석부(로컬 개발용). Redis 버전과 같은 규칙(만료 시각·연장·정리)으로 동작해
// 테스트에서 "서버 두 대가 같은 출석부를 쓰는 상황"도 그대로 흉내 낼 수 있음.
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryPresenceStore implements PresenceStore {
    // 구성원 ID → (연결 ID → 만료 시각)
    private final Map<Long, Map<String, Instant>> sessions = new HashMap<>();

    @Override
    public synchronized boolean addSession(Long membershipId, String sessionId, Instant now) {
        Map<String, Instant> memberSessions = sessions.computeIfAbsent(membershipId, key -> new HashMap<>());
        boolean wasOnline = hasLive(memberSessions, now);
        memberSessions.put(sessionId, now.plus(SESSION_TTL));
        return !wasOnline;
    }

    @Override
    public synchronized boolean removeSession(Long membershipId, String sessionId, Instant now) {
        Map<String, Instant> memberSessions = sessions.get(membershipId);
        if (memberSessions == null) return false;
        memberSessions.remove(sessionId);
        memberSessions.values().removeIf(expiresAt -> !expiresAt.isAfter(now));
        if (!memberSessions.isEmpty()) return false;
        sessions.remove(membershipId);
        return true;
    }

    @Override
    public synchronized void refresh(Map<Long, Set<String>> sessionIdsByMembershipId, Instant now) {
        sessionIdsByMembershipId.forEach((membershipId, sessionIds) -> {
            Map<String, Instant> memberSessions = sessions.computeIfAbsent(membershipId, key -> new HashMap<>());
            sessionIds.forEach(sessionId -> memberSessions.put(sessionId, now.plus(SESSION_TTL)));
        });
    }

    @Override
    public synchronized List<Long> sweepExpired(Instant now) {
        List<Long> wentOffline = new ArrayList<>();
        sessions.entrySet().removeIf(entry -> {
            entry.getValue().values().removeIf(expiresAt -> !expiresAt.isAfter(now));
            if (!entry.getValue().isEmpty()) return false;
            wentOffline.add(entry.getKey());
            return true;
        });
        return wentOffline;
    }

    @Override
    public synchronized boolean isOnline(Long membershipId, Instant now) {
        Map<String, Instant> memberSessions = sessions.get(membershipId);
        return memberSessions != null && hasLive(memberSessions, now);
    }

    private static boolean hasLive(Map<String, Instant> memberSessions, Instant now) {
        return memberSessions.values().stream().anyMatch(expiresAt -> expiresAt.isAfter(now));
    }
}
