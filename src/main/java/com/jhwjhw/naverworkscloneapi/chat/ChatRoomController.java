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

  // 텍스트 메시지 전송
  @PostMapping("/message/create")
  public ApiResponse<ChatRoomResponse.Message> message(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId,
      @Valid @RequestBody ChatRoomRequest.MessageCreateRequest r) {
    return ApiResponse.success("CHAT_MESSAGE_CREATED", "메시지를 전송했습니다.", service.sendMessage(id(jwt), chatRoomId, r));
  }

  // 최신 50개 또는 커서 이전의 메시지 조회. 조회 후 현재 위치를 읽음으로 갱신.
  @GetMapping("/message")
  public ApiResponse<ChatRoomResponse.MessagePage> messages(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId,
      @RequestParam(required = false) Long cursor) {
    return ApiResponse.success("CHAT_MESSAGE_FOUND", "메시지를 조회했습니다.", service.messages(id(jwt), chatRoomId, cursor));
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
