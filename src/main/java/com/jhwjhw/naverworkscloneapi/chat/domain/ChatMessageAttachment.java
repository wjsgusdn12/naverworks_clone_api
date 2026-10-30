package com.jhwjhw.naverworkscloneapi.chat.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "chat_message_attachment")
@Getter
public class ChatMessageAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 첨부가 달린 메시지(메시지 하나에 첨부 하나)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage chatMessage;

    // 모아보기 조회를 메시지 테이블 거치지 않고 하기 위해 방도 함께 기록.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatAttachmentKind kind;

    // 사용자가 올린 원래 파일 이름(내려받을 때 이 이름으로 저장됨)
    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long size;

    // 서버 디스크에서 파일을 찾는 위치 키(UUID 기반이라 원래 이름과 무관)
    @Column(nullable = false, length = 100)
    private String storageKey;

    // 사진 크기. 화면이 사진을 받기 전에 자리를 잡는 데 사용(알 수 없으면 null).
    private Integer width;
    private Integer height;

    @Column(nullable = false)
    private Instant createdAt;

    protected ChatMessageAttachment() {
    }

    public ChatMessageAttachment(ChatMessage chatMessage, ChatAttachmentKind kind, String originalName,
            String contentType, long size, String storageKey, Integer width, Integer height) {
        this.chatMessage = chatMessage;
        this.chatRoom = chatMessage.getChatRoom();
        this.kind = kind;
        this.originalName = originalName;
        this.contentType = contentType;
        this.size = size;
        this.storageKey = storageKey;
        this.width = width;
        this.height = height;
        this.createdAt = Instant.now();
    }

    // 전달한 메시지용 복사본. 파일은 다시 저장하지 않고 같은 위치 키를 가리킴.
    public ChatMessageAttachment copyFor(ChatMessage target) {
        return new ChatMessageAttachment(target, kind, originalName, contentType, size, storageKey, width, height);
    }
}
