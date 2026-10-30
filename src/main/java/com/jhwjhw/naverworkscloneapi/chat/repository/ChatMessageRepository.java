package com.jhwjhw.naverworkscloneapi.chat.repository;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessage;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Override
    @EntityGraph(attributePaths = {"senderMembership", "senderMembership.userAccount", "replyToMessage"})
    Optional<ChatMessage> findById(Long id);
    // 채팅방 메시지 최근 50개 조회
    @EntityGraph(attributePaths = {"senderMembership", "senderMembership.userAccount", "replyToMessage"})
    List<ChatMessage> findTop50ByChatRoomIdOrderByIdDesc(Long chatRoomId);

    // 이전 커서보다 작은 메시지를 최신순으로 읽어 Cursor Pagination을 구성.
    @EntityGraph(attributePaths = {"senderMembership", "senderMembership.userAccount", "replyToMessage"})
    List<ChatMessage> findTop50ByChatRoomIdAndIdLessThanOrderByIdDesc(Long chatRoomId, Long cursor);

    // 원문 이동 시 앞쪽 50개만 조회해 전체 과거 기록을 반복 다운로드하지 않음.
    @EntityGraph(attributePaths = {"senderMembership", "senderMembership.userAccount", "replyToMessage"})
    List<ChatMessage> findTop50ByChatRoomIdAndIdLessThanEqualOrderByIdDesc(Long chatRoomId, Long anchor);

    // 과거 원문에서 아래로 스크롤할 때 더 최근 메시지를 순서대로 조회.
    @EntityGraph(attributePaths = {"senderMembership", "senderMembership.userAccount", "replyToMessage"})
    List<ChatMessage> findTop50ByChatRoomIdAndIdGreaterThanOrderByIdAsc(Long chatRoomId, Long cursor);

    boolean existsByChatRoomIdAndIdGreaterThan(Long chatRoomId, Long messageId);

    // 상단 검색 [메시지]: 내가 참여 중인 방(숨긴 방 포함)에서 키워드가 들어간 일반 메시지를 최신순으로.
    // 삭제된 메시지·시스템 안내·첨부 메시지(파일 탭에서 찾음)는 제외.
    // pattern은 '%키워드%' 형태이며 키워드 속 %·_는 !로 이스케이프돼 글자 그대로 찾음.
    @Query("""
            select m from ChatMessage m
            join fetch m.senderMembership s
            join fetch s.userAccount
            join fetch m.chatRoom r
            where r.id in (select member.chatRoom.id from ChatRoomMember member
                           where member.membership.id = :membershipId and member.leftAt is null)
              and r.endedAt is null and m.deletedAt is null and m.messageType = :type
              and lower(m.content) like :pattern escape '!'
              and not exists (select a.id from ChatMessageAttachment a where a.chatMessage = m)
              and m.id < :cursor
            order by m.id desc
            """)
    List<ChatMessage> searchByContent(@Param("membershipId") Long membershipId, @Param("type") ChatMessageType type,
            @Param("pattern") String pattern, @Param("cursor") Long cursor, Pageable pageable);

    // 페이지에 담긴 메시지들의 멘션 대상을 쿼리 한 번으로 조회(N+1 방지).
    // mentionedMembershipIds는 별도 테이블이라 메시지마다 getter를 부르면 건수만큼 쿼리가 나감.
    @Query("select message.id as messageId, membershipId as membershipId from ChatMessage message "
            + "join message.mentionedMembershipIds membershipId where message.id in :messageIds")
    List<ChatMessageMentionRow> findMentionsByMessageIds(@Param("messageIds") List<Long> messageIds);

    // 읽음 위치 갱신에는 최신 메시지 ID 하나만 필요하므로 50건을 내려받지 않고 집계만 조회.
    @Query("select max(message.id) from ChatMessage message where message.chatRoom.id = :roomId")
    Long findMaxIdByChatRoomId(@Param("roomId") Long roomId);

    // 각 방의 마지막 메시지를 한 번에 조회.
    @EntityGraph(attributePaths = {"senderMembership", "senderMembership.userAccount"})
    @Query("select message from ChatMessage message where message.id in ("
            + "select max(innerMessage.id) from ChatMessage innerMessage where innerMessage.chatRoom.id in :roomIds group by innerMessage.chatRoom.id)")
    List<ChatMessage> findLatestByChatRoomIdIn(@Param("roomIds") List<Long> roomIds);

    // 목록의 안 읽음 수와 가장 오래된 안 읽음 시각을 방별로 집계.
    @Query("select message.chatRoom.id as roomId, count(message) as unreadCount, min(message.createdAt) as oldestUnreadMessageAt "
            + "from ChatMessage message, ChatRoomMember member where message.chatRoom.id in :roomIds "
            + "and member.chatRoom.id = message.chatRoom.id and member.membership.id = :membershipId and member.leftAt is null "
            + "and message.messageType = :messageType and message.deletedAt is null and message.senderMembership.id <> :membershipId "
            + "and (member.lastReadMessageId is null or message.id > member.lastReadMessageId) group by message.chatRoom.id")
    List<ChatRoomUnreadSummary> findUnreadSummaryByChatRoomIds(@Param("roomIds") List<Long> roomIds,
            @Param("membershipId") Long membershipId, @Param("messageType") ChatMessageType messageType);

    // 메시지별 읽음 인원을 한 번에 계산.
    @Query("select message.id as messageId, count(member) as recipientCount, "
            + "coalesce(sum(case when member.lastReadMessageId is not null and member.lastReadMessageId >= message.id then 1 else 0 end), 0) as readCount "
            + "from ChatMessage message left join ChatRoomMember member on member.chatRoom.id = message.chatRoom.id "
            + "and member.leftAt is null and member.membership.id <> message.senderMembership.id "
            + "where message.id in :messageIds and message.messageType = :messageType and message.deletedAt is null group by message.id")
    List<ChatMessageReadSummary> findReadSummaryByMessageIds(@Param("messageIds") List<Long> messageIds,
            @Param("messageType") ChatMessageType messageType);

    // 읽지 않은 일반 텍스트만 집계. 시스템 안내문은 안 읽은 수에 포함하지 않음.
    @Query("select count(message) from ChatMessage message where message.chatRoom.id = :roomId "
            + "and message.messageType = :messageType and message.deletedAt is null and message.senderMembership.id <> :membershipId "
            + "and (:lastReadMessageId is null or message.id > :lastReadMessageId)")
    long countUnread(@Param("roomId") Long roomId, @Param("membershipId") Long membershipId,
            @Param("lastReadMessageId") Long lastReadMessageId, @Param("messageType") ChatMessageType messageType);

    // 안 읽은 방을 오래된 순으로 정렬할 때 사용할 첫 미확인 메시지 시각.
    @Query("select min(message.createdAt) from ChatMessage message where message.chatRoom.id = :roomId "
            + "and message.messageType = :messageType and message.deletedAt is null and message.senderMembership.id <> :membershipId "
            + "and (:lastReadMessageId is null or message.id > :lastReadMessageId)")
    Instant findOldestUnreadAt(@Param("roomId") Long roomId, @Param("membershipId") Long membershipId,
            @Param("lastReadMessageId") Long lastReadMessageId, @Param("messageType") ChatMessageType messageType);
}
