package com.jhwjhw.naverworkscloneapi.presence;

// 접속 상태 변경을 브라우저들에게 알리는 "안내 방송".
// 브라우저는 자기가 붙은 서버에서만 소식을 받으므로, 서버가 여러 대면 다른 서버에도 전해야 함.
// - Redis 버전: Redis 방송 채널(pub/sub)로 모든 서버에 보내고, 각 서버가 자기 브라우저들에게 전달(배포용).
// - 이 서버 버전: 바로 이 서버의 브라우저들에게 전달(로컬 개발용).
public interface PresenceBroadcaster {
    String TOPIC = "/topic/presence";

    void broadcast(PresenceRealtimeEvent event);
}
