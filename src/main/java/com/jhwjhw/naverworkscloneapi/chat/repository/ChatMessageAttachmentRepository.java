package com.jhwjhw.naverworkscloneapi.chat.repository;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatAttachmentKind;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageAttachmentRepository extends JpaRepository<ChatMessageAttachment, Long> {
    Optional<ChatMessageAttachment> findByChatMessageId(Long chatMessageId);

    boolean existsByChatMessageId(Long chatMessageId);

    // 메시지 페이지의 첨부를 한 번에 읽음.
    List<ChatMessageAttachment> findByChatMessageIdIn(Collection<Long> chatMessageIds);

    // 모아보기: 삭제되지 않은 메시지의 첨부를 최신순으로. cursor보다 오래된 것만.
    @Query("""
            select a from ChatMessageAttachment a
            join fetch a.chatMessage m
            join fetch m.senderMembership s
            join fetch s.userAccount
            where a.chatRoom.id = :roomId and a.kind = :kind and m.deletedAt is null and a.id < :cursor
            order by a.id desc
            """)
    List<ChatMessageAttachment> findGallery(@Param("roomId") Long roomId, @Param("kind") ChatAttachmentKind kind,
            @Param("cursor") Long cursor, Pageable pageable);
}
