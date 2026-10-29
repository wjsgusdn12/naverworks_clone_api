package com.jhwjhw.naverworkscloneapi.chat.domain;

import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "chat_room")
@Getter
public class ChatRoom {
    // 채팅방 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회사
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // 채팅방 타입
    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    private ChatRoomType roomType;

    // 채팅방 이름
    private String name;

    // 방장
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_membership_id")
    private Membership ownerMembership;

    // 채팅방 종료 시간
    private Instant endedAt;

    // 생성 시간
    @Column(nullable = false)
    private Instant createdAt;

    // 방 공지. 방당 하나만 두고, 새로 등록하면 교체. 등록한 사람과 시각을 함께 보관.
    @Column(name = "notice_message_id")
    private Long noticeMessageId;
    @Column(name = "notice_set_by_membership_id")
    private Long noticeSetByMembershipId;
    private Instant noticeSetAt;

    protected ChatRoom() {
    }

    public ChatRoom(Company company, ChatRoomType roomType, String name, Membership ownerMembership) {
        this.company = company;
        this.roomType = roomType;
        this.name = name;
        this.ownerMembership = ownerMembership;
        this.createdAt = Instant.now();
    }

    // 방장 위임
    public void transferOwner(Membership ownerMembership) {
        this.ownerMembership = ownerMembership;
    }

    // 그룹 채팅방의 이름만 변경.
    public void rename(String name) {
        this.name = name;
    }

    // 공지 등록(기존 공지가 있으면 교체)
    public void updateNotice(Long messageId, Long membershipId) {
        this.noticeMessageId = messageId;
        this.noticeSetByMembershipId = membershipId;
        this.noticeSetAt = Instant.now();
    }

    // 공지 해제
    public void clearNotice() {
        this.noticeMessageId = null;
        this.noticeSetByMembershipId = null;
        this.noticeSetAt = null;
    }

    // 채팅방 종료
    public void end() {
        this.endedAt = Instant.now();
    }
}
