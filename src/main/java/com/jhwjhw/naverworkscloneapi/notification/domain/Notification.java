package com.jhwjhw.naverworkscloneapi.notification.domain;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "notification")
@Getter
public class Notification {
    // 알림 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 알림을 받는 구성원
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    // 분류(메시지·주소록·시스템)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    // 카드 제목(예: 채팅방 이름, "로그인 알림")
    @Column(nullable = false, length = 200)
    private String title;

    // 카드 본문(예: "홍길동: 메시지 내용")
    @Column(nullable = false, length = 500)
    private String body;

    // 클릭 시 이동할 프론트 경로. 이동할 곳이 없으면 null.
    @Column(length = 300)
    private String linkPath;

    // 읽은 시각. null이면 안 읽음.
    private Instant readAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(Membership membership, NotificationCategory category, String title, String body,
            String linkPath) {
        this.membership = membership;
        this.category = category;
        this.title = title;
        this.body = body;
        this.linkPath = linkPath;
        this.createdAt = Instant.now();
    }

    // 이미 읽은 알림은 시각을 덮어쓰지 않음.
    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }
}
