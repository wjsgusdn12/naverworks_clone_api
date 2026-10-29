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

    // 채팅방 상세 응답. notices는 최근 등록이 앞(없으면 빈 목록).
    public record Detail(Room room, List<Long> memberIds, List<MemberProfile> members,
            List<ReadCursor> readCursors, List<Notice> notices) {
    }

    // 방 상단에 고정하는 공지. 원문 메시지 위치와 내용, 등록한 사람.
    public record Notice(Long messageId, String senderName, String content, Long setByMembershipId,
            String setByName, Instant setAt) {
    }

    // 구성원별 마지막 읽은 메시지 ID. 화면이 말풍선 읽음 수를 이 값으로 직접 계산해 실시간 이벤트와 어긋나지 않게 함.
    public record ReadCursor(Long membershipId, Long lastReadMessageId) {
    }

    // 1:1 채팅방 상단과 참여자 화면에 공통으로 사용하는 구성원 프로필.
    public record MemberProfile(Long membershipId, String displayName, String jobGradeName,
            String departmentName, String companyName) {
    }

    // 메시지 목록 응답
    public record Message(Long id, Long senderMembershipId, String senderName, String content, String type,
            Instant createdAt,
            long unreadCount, long readCount, List<Long> mentionedMembershipIds, Reply reply,
            Instant editedAt, Instant deletedAt, long version, List<Reaction> reactions, boolean forwarded) {
    }

    // 말풍선 아래 묶음 표시용 이모지별 집계. reactedByMe는 요청한 사용자 기준.
    public record Reaction(String emoji, long count, boolean reactedByMe) {
    }

    // 리액션 사용자 모달용. 누른 사람 프로필과 그 사람이 고른 이모지(1인 1개).
    public record ReactionMember(String emoji, MemberProfile member) {
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
