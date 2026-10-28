package com.jhwjhw.naverworkscloneapi.chat;

// WebSocket으로 내려보내는 리액션 변화분. 클라이언트는 자기 집계에 이 변화만 반영한다.
public record ChatReactionRealtimeEvent(String type, Long chatRoomId, Long messageId, String emoji, Long membershipId,
        String action) {
}
