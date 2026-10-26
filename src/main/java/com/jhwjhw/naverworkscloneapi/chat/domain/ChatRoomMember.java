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

    // 마지막으로 읽은 메시지 ID. 메시지별 읽음 행을 만들지 않고 읽음 위치만 보관.
    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    // 사용자별 목록 숨김 시각. 방과 메시지는 삭제하지 않는다.
    private Instant hiddenAt;

    // 사용자별 상단 고정 시각.
    private Instant pinnedAt;

    // 사용자별 새 메시지 알림 수신 여부.
    @Column(nullable = false)
    private boolean notificationEnabled = true;

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
        this.hiddenAt = null;
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

    // 현재 방에서 확인한 가장 최신 메시지 위치를 저장.
    public boolean markRead(Long messageId) {
        if (messageId != null && (lastReadMessageId == null || messageId > lastReadMessageId)) {
            this.lastReadMessageId = messageId;
            return true;
        }
        return false;
    }

    public void hide() {
        this.hiddenAt = Instant.now();
    }

    public void togglePin() {
        this.pinnedAt = pinnedAt == null ? Instant.now() : null;
    }

    public void updateNotification(boolean enabled) {
        this.notificationEnabled = enabled;
    }
}
