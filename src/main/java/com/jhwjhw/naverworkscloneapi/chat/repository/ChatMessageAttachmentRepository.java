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

    // 상단 검색 [파일]: 내가 참여 중인 방(숨긴 방 포함)에서 파일 이름에 키워드가 들어간 첨부를 최신순으로.
    // pattern은 '%키워드%' 형태이며 키워드 속 %·_는 !로 이스케이프돼 글자 그대로 찾음.
    @Query("""
            select a from ChatMessageAttachment a
            join fetch a.chatMessage m
            join fetch m.senderMembership s
            join fetch s.userAccount
            join fetch a.chatRoom r
            where r.id in (select member.chatRoom.id from ChatRoomMember member
                           where member.membership.id = :membershipId and member.leftAt is null)
              and r.endedAt is null and m.deletedAt is null
              and lower(a.originalName) like :pattern escape '!'
              and a.id < :cursor
            order by a.id desc
            """)
    List<ChatMessageAttachment> searchByName(@Param("membershipId") Long membershipId,
            @Param("pattern") String pattern, @Param("cursor") Long cursor, Pageable pageable);
}
