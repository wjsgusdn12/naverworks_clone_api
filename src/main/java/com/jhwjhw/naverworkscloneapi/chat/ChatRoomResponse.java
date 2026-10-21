package com.jhwjhw.naverworkscloneapi.chat;

import java.time.Instant;
import java.util.List;

public record ChatRoomResponse() {
  // 채팅방 목록 응답
  public record Room(Long id, String type, String name, Long ownerMembershipId, int memberCount, Instant createdAt) {
  }

  // 채팅방 상세 응답
  public record Detail(Room room, List<Long> memberIds) {
  }
}
