package com.jhwjhw.naverworkscloneapi.chat;

import com.jhwjhw.naverworkscloneapi.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chatRoom")
@RequiredArgsConstructor
public class ChatRoomController {
  private final ChatRoomService service;

  // JWT에서 구성원 ID를 추출하는 메서드
  private Long id(Jwt jwt) {
    return jwt.getClaim("membershipId");
  }

  // 채팅방 목록 조회
  @GetMapping
  public ApiResponse<List<ChatRoomResponse.Room>> list(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success("CHAT_ROOM_FOUND", "채팅방 목록을 조회했습니다.", service.list(id(jwt)));
  }

  // 채팅방 상세 조회
  @GetMapping("/detail")
  public ApiResponse<ChatRoomResponse.Detail> detail(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId) {
    return ApiResponse.success("CHAT_ROOM_DETAIL_FOUND", "채팅방 정보를 조회했습니다.", service.detail(id(jwt), chatRoomId));
  }

  // 1:1 채팅방 생성
  @PostMapping("/direct/create")
  public ApiResponse<ChatRoomResponse.Room> direct(@AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ChatRoomRequest.DirectCreateRequest r) {
    return ApiResponse.success("CHAT_ROOM_CREATED", "1:1 채팅방을 준비했습니다.",
        service.createDirect(id(jwt), r.targetMembershipId()));
  }

  @PostMapping("/self/create")
  public ApiResponse<ChatRoomResponse.Room> self(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success("CHAT_SELF_ROOM_READY", "나와의 채팅방을 준비했습니다.", service.createSelf(id(jwt)));
  }

  // 그룹 채팅방 생성
  @PostMapping("/group/create")
  public ApiResponse<ChatRoomResponse.Room> group(@AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ChatRoomRequest.GroupCreateRequest r) {
    return ApiResponse.success("CHAT_ROOM_CREATED", "그룹 채팅방을 만들었습니다.", service.createGroup(id(jwt), r));
  }

  // 구성원 초대
  @PostMapping("/member/invite")
  public ApiResponse<Void> invite(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId,
      @Valid @RequestBody ChatRoomRequest.MemberRequest r) {
    service.invite(id(jwt), chatRoomId, r.membershipId());
    return ApiResponse.success("CHAT_ROOM_MEMBER_INVITED", "구성원을 초대했습니다.", null);
  }

  // 구성원 내보내기
  @PostMapping("/member/remove")
  public ApiResponse<Void> remove(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId,
      @Valid @RequestBody ChatRoomRequest.MemberRequest r) {
    service.remove(id(jwt), chatRoomId, r.membershipId());
    return ApiResponse.success("CHAT_ROOM_MEMBER_REMOVED", "구성원을 내보냈습니다.", null);
  }

  // 방장 위임
  @PostMapping("/owner/update")
  public ApiResponse<Void> owner(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId,
      @Valid @RequestBody ChatRoomRequest.MemberRequest r) {
    service.transferOwner(id(jwt), chatRoomId, r.membershipId());
    return ApiResponse.success("CHAT_ROOM_OWNER_UPDATED", "방장을 위임했습니다.", null);
  }

  // 그룹명 변경
  @PostMapping("/name/update")
  public ApiResponse<Void> rename(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId,
      @Valid @RequestBody ChatRoomRequest.RenameRequest r) {
    service.rename(id(jwt), chatRoomId, r);
    return ApiResponse.success("CHAT_ROOM_NAME_UPDATED", "그룹명을 변경했습니다.", null);
  }

  // 채팅방 나가기
  @PostMapping("/leave")
  public ApiResponse<Void> leave(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId) {
    service.leave(id(jwt), chatRoomId);
    return ApiResponse.success("CHAT_ROOM_LEFT", "채팅방에서 나갔습니다.", null);
  }

  @PostMapping("/hide")
  public ApiResponse<Void> hide(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId) {
    service.hide(id(jwt), chatRoomId);
    return ApiResponse.success("CHAT_ROOM_HIDDEN", "채팅방을 숨겼습니다.", null);
  }

  @PostMapping("/pin/update")
  public ApiResponse<Void> pin(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId) {
    service.togglePin(id(jwt), chatRoomId);
    return ApiResponse.success("CHAT_ROOM_PIN_UPDATED", "채팅방 고정 상태를 변경했습니다.", null);
  }

  @PostMapping("/notification/update")
  public ApiResponse<Void> notification(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId,
      @Valid @RequestBody ChatRoomRequest.NotificationRequest r) {
    service.updateNotification(id(jwt), chatRoomId, r.enabled(), r.mode());
    return ApiResponse.success("CHAT_ROOM_NOTIFICATION_UPDATED", "알림 설정을 변경했습니다.", null);
  }

  // 텍스트 메시지 전송
  @PostMapping("/message/create")
  public ApiResponse<ChatRoomResponse.Message> message(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId,
      @Valid @RequestBody ChatRoomRequest.MessageCreateRequest r) {
    return ApiResponse.success("CHAT_MESSAGE_CREATED", "메시지를 전송했습니다.", service.sendMessage(id(jwt), chatRoomId, r));
  }

  // 작성자 본인만 메시지 수정.
  @PostMapping("/message/update/{messageId}")
  public ApiResponse<ChatRoomResponse.Message> updateMessage(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @PathVariable Long messageId,
      @Valid @RequestBody ChatRoomRequest.MessageUpdateRequest request) {
    return ApiResponse.success("CHAT_MESSAGE_UPDATED", "메시지를 수정했습니다.",
        service.updateMessage(id(jwt), chatRoomId, messageId, request));
  }

  @PostMapping("/message/delete/{messageId}")
  public ApiResponse<ChatRoomResponse.Message> deleteMessage(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @PathVariable Long messageId,
      @Valid @RequestBody ChatRoomRequest.MessageDeleteRequest request) {
    return ApiResponse.success("CHAT_MESSAGE_DELETED", "메시지를 삭제했습니다.",
        service.deleteMessage(id(jwt), chatRoomId, messageId, request.expectedVersion()));
  }

  // 리액션 추가. 이미 눌렀으면 그대로 성공(멱등).
  @PostMapping("/message/reaction/add")
  public ApiResponse<List<ChatRoomResponse.Reaction>> addReaction(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @RequestParam Long messageId, @Valid @RequestBody ChatRoomRequest.ReactionRequest r) {
    return ApiResponse.success("CHAT_REACTION_ADDED", "리액션을 추가했습니다.",
        service.addReaction(id(jwt), chatRoomId, messageId, r.emoji()));
  }

  // 리액션 취소. 누른 적 없으면 그대로 성공(멱등).
  @PostMapping("/message/reaction/remove")
  public ApiResponse<List<ChatRoomResponse.Reaction>> removeReaction(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @RequestParam Long messageId, @Valid @RequestBody ChatRoomRequest.ReactionRequest r) {
    return ApiResponse.success("CHAT_REACTION_REMOVED", "리액션을 취소했습니다.",
        service.removeReaction(id(jwt), chatRoomId, messageId, r.emoji()));
  }

  // 특정 이모지를 누른 사람 목록.
  @GetMapping("/message/reaction/member")
  public ApiResponse<List<ChatRoomResponse.MemberProfile>> reactionMembers(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @RequestParam Long messageId, @RequestParam String emoji) {
    return ApiResponse.success("CHAT_REACTION_MEMBER_FOUND", "리액션 멤버를 조회했습니다.",
        service.reactionMembers(id(jwt), chatRoomId, messageId, emoji));
  }

  // 최신·이전·다음 페이지 또는 답장 원문 위치 조회.
  @GetMapping("/message")
  public ApiResponse<ChatRoomResponse.MessagePage> messages(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId,
      @RequestParam(required = false) Long cursor,
      @RequestParam(required = false) Long anchorMessageId,
      @RequestParam(required = false) Long afterCursor) {
    return ApiResponse.success("CHAT_MESSAGE_FOUND", "메시지를 조회했습니다.",
        service.messages(id(jwt), chatRoomId, cursor, anchorMessageId, afterCursor));
  }

  // 방을 열었을 때 최신 메시지까지 읽음 처리
  @PostMapping("/read/update")
  public ApiResponse<Void> read(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId) {
    service.markRead(id(jwt), chatRoomId);
    return ApiResponse.success("CHAT_READ_UPDATED", "읽음 상태를 갱신했습니다.", null);
  }

  // 그룹 메시지의 읽은/안 읽은 멤버 목록 조회
  @GetMapping("/message/readMember")
  public ApiResponse<List<ChatRoomResponse.ReadMember>> readMembers(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @RequestParam Long messageId, @RequestParam boolean read) {
    return ApiResponse.success("CHAT_READ_MEMBER_FOUND", "읽음 멤버를 조회했습니다.",
        service.readMembers(id(jwt), chatRoomId, messageId, read));
  }
}
