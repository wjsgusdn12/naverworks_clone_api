package com.jhwjhw.naverworkscloneapi.chat;

// WebSocket 구독자에게 전달하는 읽음 위치 변경 DTO.
public record ChatReadRealtimeEvent(String type, Long chatRoomId, Long readerMembershipId, Long lastReadMessageId) {
}
