package com.jhwjhw.naverworkscloneapi.chat;

import java.time.Instant;
import java.util.List;

public record ChatRoomResponse() {
    // 채팅방 목록 응답
    public record Room(Long id, String type, String name, Long ownerMembershipId, int memberCount, Instant createdAt,
            String lastMessage, Instant lastMessageAt, Long lastSenderMembershipId, long unreadCount,
            Instant oldestUnreadMessageAt,
            Long directMembershipId, String directDisplayName, String directJobGradeName, String directDepartmentName,
            String directCompanyName,
            boolean pinned, Instant pinnedAt, boolean notificationEnabled, String notificationMode,
            boolean lastMessageMentioned, Long lastMessageId, long lastMessageVersion) {
    }

    // 채팅방 상세 응답
    public record Detail(Room room, List<Long> memberIds, List<MemberProfile> members) {
    }

    // 1:1 채팅방 상단과 참여자 화면에 공통으로 사용하는 구성원 프로필.
    public record MemberProfile(Long membershipId, String displayName, String jobGradeName,
            String departmentName, String companyName) {
    }

    // 메시지 목록 응답
    public record Message(Long id, Long senderMembershipId, String senderName, String content, String type,
            Instant createdAt,
            long unreadCount, long readCount, List<Long> mentionedMembershipIds, Reply reply,
            Instant editedAt, Instant deletedAt, long version) {
    }

    // 원문 재조회 없이 인용을 표시하기 위한 답장 당시 정보.
    public record Reply(Long messageId, String senderName, String content, boolean deleted) {
    }

    // 이전 메시지 조회 결과. nextCursor가 null이면 더 오래된 메시지가 없음.
    public record MessagePage(List<Message> messages, Long nextCursor, Long newerCursor) {
    }

    // 읽음/안 읽음 멤버 모달에 필요한 최소 프로필 정보.
    public record ReadMember(Long membershipId, String displayName, String jobGrade, String departmentName,
            String companyName, boolean read) {
    }
}
