package com.jhwjhw.naverworkscloneapi.presence;

// WebSocket 구독자에게 전달하는 구성원 온라인 상태 DTO.
public record PresenceRealtimeEvent(
        String type,
        Long membershipId,
        boolean online
) {
}
