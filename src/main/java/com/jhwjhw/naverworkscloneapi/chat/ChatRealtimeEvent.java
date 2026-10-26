package com.jhwjhw.naverworkscloneapi.chat;

// 클라이언트가 이벤트 종류를 구분할 수 있는 STOMP 전송 DTO.
public record ChatRealtimeEvent(String type, Long chatRoomId, ChatRoomResponse.Message message) {
}
