package com.jhwjhw.naverworkscloneapi.chat;

// DB에 저장하지 않는 현재 입력 상태 이벤트.
public record ChatTypingEvent(String type, Long chatRoomId, Long membershipId, String displayName, boolean typing) {
}
