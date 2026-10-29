package com.jhwjhw.naverworkscloneapi.chat.repository;

// 메시지 한 건의 멘션 대상 한 명. 페이지 단위로 한 번에 읽어 메시지마다 따로 조회하지 않게 함.
public interface ChatMessageMentionRow {
    Long getMessageId();

    Long getMembershipId();
}
