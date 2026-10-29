package com.jhwjhw.naverworkscloneapi.chat;

import java.util.List;

// 공지 등록·내리기 뒤의 방 공지 전체 목록. 화면은 이 목록으로 그대로 교체.
public record ChatNoticeChangedEvent(Long chatRoomId, List<ChatRoomResponse.Notice> notices) {
}
