package com.jhwjhw.naverworkscloneapi.chat;

// 커밋 이후 방 구독자에게 보낼 리액션 변화분. 전체 집계 대신 "누가 어떤 이모지를 추가/취소했는지"만 전달.
public record ChatReactionChangedEvent(Long chatRoomId, Long messageId, String emoji, Long membershipId,
        String action) {
    public static final String ADDED = "added";
    public static final String REMOVED = "removed";
}
