package com.jhwjhw.naverworkscloneapi.chat;

// 트랜잭션 커밋 후 WebSocket으로 전달할 내부 메시지 생성 이벤트.
public record ChatMessageCreatedEvent(Long chatRoomId, ChatRoomResponse.Message message) {
}
