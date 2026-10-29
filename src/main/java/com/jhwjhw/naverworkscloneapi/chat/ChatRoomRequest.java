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

  // 채팅방별 알림 설정 요청
  public record NotificationRequest(Boolean enabled, String mode) {
  }

  // 텍스트 메시지 전송 요청
  public record MessageCreateRequest(@NotBlank @Size(max = 2000) String content,
      List<Long> mentionedMembershipIds, @Positive Long replyToMessageId) {
  }

  // 화면에서 읽은 버전을 함께 전달해 오래된 편집의 덮어쓰기 방지.
  public record MessageUpdateRequest(@NotBlank @Size(max = 2000) String content,
      List<Long> mentionedMembershipIds, @NotNull @PositiveOrZero Long expectedVersion) {
  }

  public record MessageDeleteRequest(@NotNull @PositiveOrZero Long expectedVersion) {
  }

  // 리액션 추가·취소 요청. emoji는 코드명(thumbs_up 등).
  public record ReactionRequest(@NotBlank @Size(max = 40) String emoji) {
  }

  // 메시지 전달 요청. 한 번에 최대 10개 방.
  public record ForwardRequest(@NotEmpty @Size(max = 10) List<Long> targetChatRoomIds) {
  }
}
