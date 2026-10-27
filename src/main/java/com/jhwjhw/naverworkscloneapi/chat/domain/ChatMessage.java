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
    public static final String DELETED_CONTENT = "삭제된 메시지입니다.";
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

    // 같은 메시지를 여러 탭에서 변경해도 이전 내용이 덮어쓰이지 않도록 버전 검사.
    @Version
    private long version;
    private Instant editedAt;
    private Instant deletedAt;

    // 원문 위치는 ID로 연결하고, 인용 내용은 답장 당시 값으로 보존.
    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;
    private String replyToSenderName;
    @Column(length = 2000)
    private String replyToContent;

    // 인용 당시 내용은 보존하되, 원문 삭제 여부는 현재 상태를 확인해 응답에서 가림.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id", insertable = false, updatable = false)
    private ChatMessage replyToMessage;

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

    public void replyTo(ChatMessage original) {
        this.replyToMessageId = original.getId();
        this.replyToSenderName = original.getSenderMembership().getUserAccount().getDisplayName();
        this.replyToContent = original.getContent();
        this.replyToMessage = original;
    }

    public void editContent(String content, Set<Long> mentionedMembershipIds) {
        this.content = content;
        mention(mentionedMembershipIds);
        this.editedAt = Instant.now();
    }

    public void deleteContent() {
        // 삭제 후 본문·멘션을 다시 노출하지 않고, 원문 연결용 메시지 ID만 유지.
        this.content = DELETED_CONTENT;
        this.mentionedMembershipIds.clear();
        this.deletedAt = Instant.now();
    }
}
