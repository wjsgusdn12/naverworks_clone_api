package com.jhwjhw.naverworkscloneapi.chat.repository;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoom;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    // 주소록 "그룹" 탭용: 회사의 살아 있는 그룹 채팅방 전체. 방장 이름을 함께 읽어 방마다 추가 조회하지 않음.
    @EntityGraph(attributePaths = { "ownerMembership", "ownerMembership.userAccount" })
    List<ChatRoom> findByCompanyIdAndRoomTypeAndEndedAtIsNullOrderByCreatedAtDesc(Long companyId,
            ChatRoomType roomType);
}
