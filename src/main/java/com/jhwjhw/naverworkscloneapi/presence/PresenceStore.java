package com.jhwjhw.naverworkscloneapi.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 누가 접속 중인지 적어 두는 "공용 출석부".
// 연결(브라우저 탭) 하나마다 이름표를 달고, 이름표에는 SESSION_TTL 뒤 자동 만료 시각이 붙음.
// 각 서버가 자기 연결의 만료 시각을 주기적으로 미루므로(refresh), 서버가 갑자기 꺼지면
// 그 서버의 이름표만 미뤄지지 않고 만료돼 sweepExpired에서 오프라인으로 정리됨.
// - Redis 버전: 서버 1·2가 같은 출석부를 씀(배포용). - 메모리 버전: 이 서버 안에서만(로컬 개발용).
public interface PresenceStore {
    // 이름표 유효 시간. 서버는 이보다 짧은 간격(20초)으로 연장함.
    Duration SESSION_TTL = Duration.ofSeconds(60);

    // 연결 등록. 이 사람이 모든 서버를 통틀어 "방금" 온라인이 됐으면 true(온라인 알림을 보낼지 판단).
    boolean addSession(Long membershipId, String sessionId, Instant now);

    // 연결 해제. 이 사람의 연결이 모든 서버에서 하나도 안 남았으면 true(오프라인 알림을 보낼지 판단).
    boolean removeSession(Long membershipId, String sessionId, Instant now);

    // 이 서버에 살아 있는 연결들의 만료 시각을 지금부터 SESSION_TTL 뒤로 미룸.
    void refresh(Map<Long, Set<String>> sessionIdsByMembershipId, Instant now);

    // 만료된 이름표를 치우고, 그 때문에 연결이 하나도 안 남은 사람 목록을 돌려줌.
    List<Long> sweepExpired(Instant now);

    boolean isOnline(Long membershipId, Instant now);
}
