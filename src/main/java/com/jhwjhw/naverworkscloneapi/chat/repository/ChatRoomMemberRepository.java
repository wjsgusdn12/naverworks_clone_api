package com.jhwjhw.naverworkscloneapi.chat.repository;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomMember;
import java.util.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
        // 채팅방 구성원 조회
        Optional<ChatRoomMember> findByChatRoomIdAndMembershipId(Long chatRoomId, Long membershipId);

        // 권한 검사에 필요한 방·구성원·계정을 한 쿼리로 가져와, 요청마다 반복되던 지연 로딩 3~4회를 없앰.
        @EntityGraph(attributePaths = { "chatRoom", "membership", "membership.userAccount" })
        @Query("select member from ChatRoomMember member where member.chatRoom.id = :roomId and member.membership.id = :membershipId")
        Optional<ChatRoomMember> findWithRoomAndAccountByChatRoomIdAndMembershipId(@Param("roomId") Long roomId,
                        @Param("membershipId") Long membershipId);

        // 구성원 채팅방 목록 조회
        @EntityGraph(attributePaths = { "chatRoom", "chatRoom.ownerMembership", "membership",
                        "membership.userAccount" })
        List<ChatRoomMember> findByMembershipIdAndLeftAtIsNull(Long membershipId);

        // 숨긴 방은 해당 사용자 목록에서만 제외.
        @EntityGraph(attributePaths = { "chatRoom", "chatRoom.ownerMembership", "membership",
                        "membership.userAccount" })
        List<ChatRoomMember> findByMembershipIdAndLeftAtIsNullAndHiddenAtIsNull(Long membershipId);

        // 여러 방의 활성 구성원을 한 번에 가져와 목록 응답의 N+1 조회를 방지.
        @EntityGraph(attributePaths = { "membership", "membership.userAccount", "membership.jobGrade",
                        "membership.department", "membership.company" })
        @Query("select member from ChatRoomMember member where member.chatRoom.id in :roomIds and member.leftAt is null")
        List<ChatRoomMember> findActiveByChatRoomIdIn(@Param("roomIds") List<Long> roomIds);

        // 나간 방을 다시 열 때 기존 1:1 방을 찾기 위해 모든 참여 이력을 조회.
        List<ChatRoomMember> findByMembershipId(Long membershipId);

        // 채팅방 구성원 목록 조회
    List<ChatRoomMember> findByChatRoomIdAndLeftAtIsNull(Long chatRoomId);

    // 읽음 현황처럼 구성원 프로필까지 내려주는 조회는 연관 엔티티를 한 쿼리로 함께 가져와 N+1 조회를 방지.
    @EntityGraph(attributePaths = { "membership", "membership.userAccount", "membership.jobGrade",
            "membership.department", "membership.company" })
    @Query("select member from ChatRoomMember member where member.chatRoom.id = :roomId and member.leftAt is null")
    List<ChatRoomMember> findActiveWithProfileByChatRoomId(@Param("roomId") Long roomId);

    // STOMP 구독 전에 현재 사용자가 활성 참여자인지 한 번의 exists 쿼리로 검증.
    @Query("select case when count(member) > 0 then true else false end from ChatRoomMember member "
            + "where member.chatRoom.id = :roomId and member.membership.id = :membershipId "
            + "and member.leftAt is null and member.chatRoom.endedAt is null")
    boolean existsActiveMember(@Param("roomId") Long roomId, @Param("membershipId") Long membershipId);

        // 특정 메시지를 아직 읽지 않은 활성 구성원 수. 보낸 사람은 제외.
        @Query("select count(member) from ChatRoomMember member where member.chatRoom.id = :roomId "
                        + "and member.leftAt is null and member.membership.id <> :senderMembershipId "
                        + "and (member.lastReadMessageId is null or member.lastReadMessageId < :messageId)")
        long countUnreadMembers(@Param("roomId") Long roomId, @Param("senderMembershipId") Long senderMembershipId,
                        @Param("messageId") Long messageId);

        // 보낸 사람을 제외한 현재 참여자 수. 읽음 수 계산의 기준값으로 사용.
        @Query("select count(member) from ChatRoomMember member where member.chatRoom.id = :roomId "
                        + "and member.leftAt is null and member.membership.id <> :senderMembershipId")
        long countReadableMembers(@Param("roomId") Long roomId, @Param("senderMembershipId") Long senderMembershipId);
}
