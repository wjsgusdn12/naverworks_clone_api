package com.jhwjhw.naverworkscloneapi.chat.repository;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessage;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 채팅방 메시지 최근 50개 조회
    List<ChatMessage> findTop50ByChatRoomIdOrderByIdDesc(Long chatRoomId);

    // 이전 커서보다 작은 메시지를 최신순으로 읽어 Cursor Pagination을 구성.
    List<ChatMessage> findTop50ByChatRoomIdAndIdLessThanOrderByIdDesc(Long chatRoomId, Long cursor);

    // 읽지 않은 일반 텍스트만 집계. 시스템 안내문은 안 읽은 수에 포함하지 않음.
    @Query("select count(message) from ChatMessage message where message.chatRoom.id = :roomId "
            + "and message.messageType = :messageType and message.senderMembership.id <> :membershipId "
            + "and (:lastReadMessageId is null or message.id > :lastReadMessageId)")
    long countUnread(@Param("roomId") Long roomId, @Param("membershipId") Long membershipId,
            @Param("lastReadMessageId") Long lastReadMessageId, @Param("messageType") ChatMessageType messageType);
}
