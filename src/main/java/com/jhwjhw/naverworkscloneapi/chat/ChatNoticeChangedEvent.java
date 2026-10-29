package com.jhwjhw.naverworkscloneapi.chat;

// 공지 등록·교체·해제. notice가 null이면 해제.
public record ChatNoticeChangedEvent(Long chatRoomId, ChatRoomResponse.Notice notice) {
}
