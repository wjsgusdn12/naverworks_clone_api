package com.jhwjhw.naverworkscloneapi.chat.repository;

// 메시지·이모지별 집계. mineCount가 1 이상이면 요청한 사용자가 누른 이모지.
public interface ChatMessageReactionSummary {
    Long getMessageId();

    String getEmoji();

    long getCount();

    long getMineCount();
}
