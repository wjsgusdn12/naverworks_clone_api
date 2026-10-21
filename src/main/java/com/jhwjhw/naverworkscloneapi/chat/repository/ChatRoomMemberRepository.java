package com.jhwjhw.naverworkscloneapi.chat.repository;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomMember;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
    // 채팅방 구성원 조회
    Optional<ChatRoomMember> findByChatRoomIdAndMembershipId(Long chatRoomId, Long membershipId);

    // 구성원 채팅방 목록 조회
    List<ChatRoomMember> findByMembershipIdAndLeftAtIsNull(Long membershipId);

    // 나간 방을 다시 열 때 기존 1:1 방을 찾기 위해 모든 참여 이력을 조회.
    List<ChatRoomMember> findByMembershipId(Long membershipId);

    // 채팅방 구성원 목록 조회
    List<ChatRoomMember> findByChatRoomIdAndLeftAtIsNull(Long chatRoomId);

    // 특정 메시지를 아직 읽지 않은 활성 구성원 수. 보낸 사람은 제외.
    @Query("select count(member) from ChatRoomMember member where member.chatRoom.id = :roomId "
            + "and member.leftAt is null and member.membership.id <> :senderMembershipId "
            + "and (member.lastReadMessageId is null or member.lastReadMessageId < :messageId)")
    long countUnreadMembers(@Param("roomId") Long roomId, @Param("senderMembershipId") Long senderMembershipId,
            @Param("messageId") Long messageId);
}
