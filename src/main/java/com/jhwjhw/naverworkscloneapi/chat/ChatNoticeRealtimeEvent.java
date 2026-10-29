package com.jhwjhw.naverworkscloneapi.chat;

// 방 구독자에게 보내는 공지 변경 이벤트. notice가 null이면 공지 띠를 내림.
public record ChatNoticeRealtimeEvent(String type, Long chatRoomId, ChatRoomResponse.Notice notice) {
}
