package com.jhwjhw.naverworkscloneapi.chat.domain;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "chat_room_notice")
@Getter
public class ChatRoomNotice {
    // 방당 둘 수 있는 공지 수 상한.
    public static final int MAX_PER_ROOM = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 공지가 걸린 방
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    // 공지로 올린 메시지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage chatMessage;

    // 등록한 구성원
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_by_membership_id", nullable = false)
    private Membership setByMembership;

    @Column(nullable = false)
    private Instant createdAt;

    // 공지를 내린 시각. 값이 있으면 목록에서 제외(논리 삭제).
    private Instant removedAt;

    protected ChatRoomNotice() {
    }

    public ChatRoomNotice(ChatRoom chatRoom, ChatMessage chatMessage, Membership setByMembership) {
        this.chatRoom = chatRoom;
        this.chatMessage = chatMessage;
        this.setByMembership = setByMembership;
        this.createdAt = Instant.now();
    }

    // 공지 내리기
    public void remove() {
        this.removedAt = Instant.now();
    }
}
