package com.jhwjhw.naverworkscloneapi.chat.domain;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;

@Entity
@Table(name = "chat_message_reaction")
@Getter
public class ChatMessageReaction {
    // 화면의 빠른 선택 바에 노출하는 이모지. 이미지 이모지로 바꿔도 되도록 문자 대신 코드명으로 저장.
    public static final Set<String> ALLOWED_EMOJIS = Set.of("thumbs_up", "heart", "laugh", "wow", "sad", "pray");
    // 한 메시지에 붙을 수 있는 서로 다른 이모지 수 상한.
    public static final int MAX_DISTINCT_EMOJIS = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 리액션이 달린 메시지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage chatMessage;

    // 누른 구성원
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @Column(nullable = false, length = 40)
    private String emoji;

    @Column(nullable = false)
    private Instant createdAt;

    protected ChatMessageReaction() {
    }

    public ChatMessageReaction(ChatMessage chatMessage, Membership membership, String emoji) {
        this.chatMessage = chatMessage;
        this.membership = membership;
        this.emoji = emoji;
        this.createdAt = Instant.now();
    }
}
