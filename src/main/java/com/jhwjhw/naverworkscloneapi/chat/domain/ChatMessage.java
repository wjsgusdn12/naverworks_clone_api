package com.jhwjhw.naverworkscloneapi.chat.domain;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;

@Entity
@Table(name = "chat_message")
@Getter
public class ChatMessage {
    // 메시지 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 채팅방
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    // 보낸 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_membership_id", nullable = false)
    private Membership senderMembership;
    
    // 메시지 내용
    @Column(nullable = false, length = 2000)
    private String content;

    // 메시지 타입
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private ChatMessageType messageType;

    // 생성 시간
    @Column(nullable = false)
    private Instant createdAt;

    // 메시지 본문과 분리해 저장한 멘션 대상. @All은 서버에서 활성 구성원 ID로 확장한다.
    @ElementCollection
    @CollectionTable(name = "chat_message_mention", joinColumns = @JoinColumn(name = "chat_message_id"))
    @Column(name = "membership_id")
    private Set<Long> mentionedMembershipIds = new LinkedHashSet<>();

    protected ChatMessage() {
    }

    public ChatMessage(ChatRoom room, Membership sender, String content, ChatMessageType type) {
        this.chatRoom = room;
        this.senderMembership = sender;
        this.content = content;
        this.messageType = type;
        this.createdAt = Instant.now();
    }

    public void mention(Set<Long> membershipIds) {
        this.mentionedMembershipIds = new LinkedHashSet<>(membershipIds);
    }
}
