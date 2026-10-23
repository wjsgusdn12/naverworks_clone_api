package com.jhwjhw.naverworkscloneapi.chat.repository;

// 메시지 목록의 읽음 상태를 메시지별 반복 조회 없이 집계.
public interface ChatMessageReadSummary {
    Long getMessageId();
    long getRecipientCount();
    long getReadCount();
}
