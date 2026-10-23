package com.jhwjhw.naverworkscloneapi.chat.repository;

import java.time.Instant;

// 메시지방 목록을 만들 때 방별 미확인 메시지를 한 번에 집계.
public interface ChatRoomUnreadSummary {
    Long getRoomId();
    long getUnreadCount();
    Instant getOldestUnreadMessageAt();
}
