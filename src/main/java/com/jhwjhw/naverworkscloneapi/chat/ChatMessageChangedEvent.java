package com.jhwjhw.naverworkscloneapi.chat;

// 수정·삭제도 DB 커밋 후에만 전파해 롤백된 내용이 화면에 노출되는 상황 방지.
public record ChatMessageChangedEvent(String type, Long chatRoomId, ChatRoomResponse.Message message) {
}
