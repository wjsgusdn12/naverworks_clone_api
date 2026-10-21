package com.jhwjhw.naverworkscloneapi.chat;

import jakarta.validation.constraints.*;
import java.util.List;

public record ChatRoomRequest() {
  // 1:1 채팅방 생성 요청
  public record DirectCreateRequest(@NotNull Long targetMembershipId) {
  }

  // 그룹 채팅방 생성 요청
  public record GroupCreateRequest(@NotBlank @Size(max = 100) String name, @NotEmpty List<Long> membershipIds) {
  }

  // 구성원 초대 요청
  public record MemberRequest(@NotNull Long membershipId) {
  }

  // 그룹 채팅방 이름 변경 요청
  public record RenameRequest(@NotBlank @Size(max = 100) String name) {
  }

  // 텍스트 메시지 전송 요청
  public record MessageCreateRequest(@NotBlank @Size(max = 2000) String content) {
  }
}
