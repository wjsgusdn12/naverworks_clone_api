package com.jhwjhw.naverworkscloneapi.presence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

// Redis 버전 출석부(배포용). 서버 1·2가 같은 출석부를 쓰므로 어느 서버에 붙은 사람이든 똑같이 온라인으로 보임.
//
// 저장 모양(정렬 집합 ZSET: 값마다 점수를 붙여 두는 목록. 여기서는 점수 = 만료 시각(ms))
// - presence:sessions:{구성원ID}  연결 ID → 만료 시각
// - presence:online                구성원 ID → 가장 늦은 만료 시각(온라인 여부를 한 번에 확인하는 용도)
//
// "확인하고 → 바꾸는" 두 단계 사이에 다른 서버가 끼어들면 결과가 꼬이므로,
// 그런 작업은 Lua 스크립트(Redis 안에서 한 번에 처리되는 묶음 명령)로 실행함.
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RedisPresenceStore implements PresenceStore {
    private static final String ONLINE_KEY = "presence:online";
    private static final String SESSIONS_KEY = "presence:sessions:";
    // 연결 목록 키 자체도 넉넉한 시간 뒤 지워지게 해 찌꺼기가 남지 않게 함.
    private static final long SESSIONS_KEY_TTL_SECONDS = SESSION_TTL.toSeconds() * 2;

    // 연결 등록: 등록 전에 이미 살아 있는 온라인 기록이 있었는지 보고, 없었으면 1(방금 온라인).
    private static final DefaultRedisScript<Long> ADD_SCRIPT = new DefaultRedisScript<>("""
            local previous = redis.call('ZSCORE', KEYS[1], ARGV[1])
            redis.call('ZADD', KEYS[2], ARGV[4], ARGV[2])
            redis.call('EXPIRE', KEYS[2], ARGV[5])
            redis.call('ZADD', KEYS[1], ARGV[4], ARGV[1])
            if (not previous) or tonumber(previous) <= tonumber(ARGV[3]) then return 1 end
            return 0
            """, Long.class);

    // 연결 해제: 이 연결과 만료된 연결을 지우고, 남은 연결이 없으면 온라인 목록에서 빼고 1.
    // 서버 두 대가 동시에 해도 온라인 목록에서 실제로 뺀 한 대만 1을 받아 오프라인 알림이 한 번만 나감.
    private static final DefaultRedisScript<Long> REMOVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREM', KEYS[2], ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[3])
            if redis.call('ZCARD', KEYS[2]) > 0 then return 0 end
            redis.call('DEL', KEYS[2])
            return redis.call('ZREM', KEYS[1], ARGV[1])
            """, Long.class);

    // 정리: 만료된 연결을 지우고, 남은 연결이 없고 그사이 아무도 연장하지 않았으면 온라인 목록에서 빼고 1.
    private static final DefaultRedisScript<Long> SWEEP_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[2])
            if redis.call('ZCARD', KEYS[2]) > 0 then return 0 end
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if score and tonumber(score) <= tonumber(ARGV[2]) then
                redis.call('ZREM', KEYS[1], ARGV[1])
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    @Override
    public boolean addSession(Long membershipId, String sessionId, Instant now) {
        Long result = redis.execute(ADD_SCRIPT, List.of(ONLINE_KEY, SESSIONS_KEY + membershipId),
                membershipId.toString(), sessionId, millis(now), millis(now.plus(SESSION_TTL)),
                String.valueOf(SESSIONS_KEY_TTL_SECONDS));
        return Long.valueOf(1).equals(result);
    }

    @Override
    public boolean removeSession(Long membershipId, String sessionId, Instant now) {
        Long result = redis.execute(REMOVE_SCRIPT, List.of(ONLINE_KEY, SESSIONS_KEY + membershipId),
                membershipId.toString(), sessionId, millis(now));
        return Long.valueOf(1).equals(result);
    }

    @Override
    public void refresh(Map<Long, Set<String>> sessionIdsByMembershipId, Instant now) {
        if (sessionIdsByMembershipId.isEmpty()) return;
        double expiresAt = now.plus(SESSION_TTL).toEpochMilli();
        // 연결이 많아도 한 번에 묶어 보냄(파이프라인).
        redis.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                sessionIdsByMembershipId.forEach((membershipId, sessionIds) -> {
                    String sessionsKey = SESSIONS_KEY + membershipId;
                    sessionIds.forEach(sessionId -> ops.opsForZSet().add(sessionsKey, sessionId, expiresAt));
                    ops.expire(sessionsKey, java.time.Duration.ofSeconds(SESSIONS_KEY_TTL_SECONDS));
                    ops.opsForZSet().add(ONLINE_KEY, membershipId.toString(), expiresAt);
                });
                return null;
            }
        });
    }

    @Override
    public List<Long> sweepExpired(Instant now) {
        // 만료 시각이 지난 사람만 후보로 골라 한 명씩 확인.
        Set<String> candidates = redis.opsForZSet().rangeByScore(ONLINE_KEY, 0, now.toEpochMilli());
        List<Long> wentOffline = new ArrayList<>();
        if (candidates == null) return wentOffline;
        for (String candidate : candidates) {
            Long result = redis.execute(SWEEP_SCRIPT, List.of(ONLINE_KEY, SESSIONS_KEY + candidate), candidate,
                    millis(now));
            if (Long.valueOf(1).equals(result)) wentOffline.add(Long.valueOf(candidate));
        }
        return wentOffline;
    }

    @Override
    public boolean isOnline(Long membershipId, Instant now) {
        Double expiresAt = redis.opsForZSet().score(ONLINE_KEY, membershipId.toString());
        return expiresAt != null && expiresAt > now.toEpochMilli();
    }

    private static String millis(Instant instant) {
        return String.valueOf(instant.toEpochMilli());
    }
}
