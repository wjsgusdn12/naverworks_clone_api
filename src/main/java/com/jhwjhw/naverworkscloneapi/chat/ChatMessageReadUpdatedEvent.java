package com.jhwjhw.naverworkscloneapi.chat;

// 채팅방 참여자가 새 메시지 위치까지 읽었음을 알리는 내부 이벤트.
public record ChatMessageReadUpdatedEvent(Long chatRoomId, Long readerMembershipId, Long lastReadMessageId) {
}
