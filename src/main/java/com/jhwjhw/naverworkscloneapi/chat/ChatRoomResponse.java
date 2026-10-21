package com.jhwjhw.naverworkscloneapi.chat;

import java.time.Instant;
import java.util.List;

public record ChatRoomResponse() {
  // 채팅방 목록 응답
  public record Room(Long id, String type, String name, Long ownerMembershipId, int memberCount, Instant createdAt,
      String lastMessage, Instant lastMessageAt, long unreadCount) {
  }

  // 채팅방 상세 응답
  public record Detail(Room room, List<Long> memberIds) {
  }

  // 메시지 목록 응답
  public record Message(Long id, Long senderMembershipId, String senderName, String content, String type, Instant createdAt,
      Long unreadCount) {
  }

  // 이전 메시지 조회 결과. nextCursor가 null이면 더 오래된 메시지가 없음.
  public record MessagePage(List<Message> messages, Long nextCursor) {
  }

  // 읽음/안 읽음 멤버 모달에 필요한 최소 프로필 정보.
  public record ReadMember(Long membershipId, String displayName, String jobGrade, String departmentName, boolean read) {
  }
}
