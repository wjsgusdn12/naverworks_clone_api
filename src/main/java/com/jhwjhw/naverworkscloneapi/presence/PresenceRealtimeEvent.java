package com.jhwjhw.naverworkscloneapi.presence;

// WebSocket 구독자에게 전달하는 구성원 접속 상태 DTO.
// online은 연결 여부, status는 부재중까지 반영한 화면 표시용 상태(ONLINE/OFFLINE/AWAY).
public record PresenceRealtimeEvent(
        String type,
        Long membershipId,
        boolean online,
        String status
) {
}
