package com.jhwjhw.naverworkscloneapi.chat.repository;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomMember;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
    // 채팅방 구성원 조회
    Optional<ChatRoomMember> findByChatRoomIdAndMembershipId(Long chatRoomId, Long membershipId);

    // 구성원 채팅방 목록 조회
    List<ChatRoomMember> findByMembershipIdAndLeftAtIsNull(Long membershipId);

    // 채팅방 구성원 목록 조회
    List<ChatRoomMember> findByChatRoomIdAndLeftAtIsNull(Long chatRoomId);
}
