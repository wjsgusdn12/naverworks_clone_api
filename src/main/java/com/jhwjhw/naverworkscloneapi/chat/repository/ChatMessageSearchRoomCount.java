package com.jhwjhw.naverworkscloneapi.chat.repository;

// 상단 검색 [메시지]의 방별 묶음: 방마다 키워드가 들어간 메시지 수와 가장 최근 메시지 ID(정렬용).
public interface ChatMessageSearchRoomCount {
    Long getRoomId();
    long getMatchCount();
    Long getLatestMessageId();
}
