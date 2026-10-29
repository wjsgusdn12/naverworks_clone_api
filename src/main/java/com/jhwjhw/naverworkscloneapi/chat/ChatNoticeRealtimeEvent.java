package com.jhwjhw.naverworkscloneapi.chat;

import java.util.List;

// 방 구독자에게 보내는 공지 변경 이벤트. 최신 공지 목록 전체를 담음(비어 있으면 공지 없음).
public record ChatNoticeRealtimeEvent(String type, Long chatRoomId, List<ChatRoomResponse.Notice> notices) {
}
