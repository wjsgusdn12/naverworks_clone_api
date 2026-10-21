package com.jhwjhw.naverworkscloneapi.chat.domain;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "chat_room_member")
@Getter
public class ChatRoomMember {
    // 채팅방 구성원 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 채팅방
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    // 구성원
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    // 참여 시간
    @Column(nullable = false)
    private Instant joinedAt;

    // 나간 시간
    private Instant leftAt;

    protected ChatRoomMember() {
    }

    public ChatRoomMember(ChatRoom chatRoom, Membership membership) {
        this.chatRoom = chatRoom;
        this.membership = membership;
        this.joinedAt = Instant.now();
    }

    // 참여 재시작
    public void rejoin() {
        this.leftAt = null;
        this.joinedAt = Instant.now();
    }

    // 나가기
    public void leave() {
        this.leftAt = Instant.now();
    }

    // 활동 중인지 확인
    public boolean isActive() {
        return leftAt == null;
    }
}
