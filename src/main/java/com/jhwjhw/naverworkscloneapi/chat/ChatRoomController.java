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

  // 채팅방 나가기
  @PostMapping("/leave")
  public ApiResponse<Void> leave(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId) {
    service.leave(id(jwt), chatRoomId);
    return ApiResponse.success("CHAT_ROOM_LEFT", "채팅방에서 나갔습니다.", null);
  }
}
