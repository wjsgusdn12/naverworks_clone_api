package com.jhwjhw.naverworkscloneapi.chat.repository;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomNotice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomNoticeRepository extends JpaRepository<ChatRoomNotice, Long> {
    // 방에 걸린 공지 목록(최근 등록이 앞). 메시지·보낸 사람·등록한 사람을 함께 가져와 추가 조회를 막음.
    @EntityGraph(attributePaths = { "chatMessage", "chatMessage.senderMembership",
            "chatMessage.senderMembership.userAccount", "setByMembership", "setByMembership.userAccount" })
    List<ChatRoomNotice> findByChatRoomIdAndRemovedAtIsNullOrderByCreatedAtDesc(Long chatRoomId);

    // 같은 메시지를 두 번 올리지 않도록 확인.
    Optional<ChatRoomNotice> findByChatRoomIdAndChatMessageIdAndRemovedAtIsNull(Long chatRoomId, Long chatMessageId);

    long countByChatRoomIdAndRemovedAtIsNull(Long chatRoomId);
}
