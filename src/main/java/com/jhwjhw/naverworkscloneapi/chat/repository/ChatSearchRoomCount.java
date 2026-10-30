package com.jhwjhw.naverworkscloneapi.chat.repository;

// 상단 검색의 방별 묶음: 방마다 키워드에 맞는 메시지(또는 파일) 수와 가장 최근 항목 ID(정렬용).
public interface ChatSearchRoomCount {
    Long getRoomId();
    long getMatchCount();
    Long getLatestId();
}
