package com.jhwjhw.naverworkscloneapi.chat;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatAttachmentKind;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageAttachment;
import com.jhwjhw.naverworkscloneapi.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
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

  // 주소록 "그룹" 탭: 내 회사의 그룹 채팅방 전체 요약.
  @GetMapping("/group/list")
  public ApiResponse<List<ChatRoomResponse.GroupSummary>> groupRooms(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success("CHAT_GROUP_ROOM_FOUND", "그룹 채팅방 목록을 조회했습니다.",
        service.groupRooms(id(jwt)));
  }

  // 주소록 그룹 상세 패널: 그룹 채팅방 구성원 목록(즐겨찾기·방장 여부 포함).
  @GetMapping("/group/members")
  public ApiResponse<List<ChatRoomResponse.GroupMember>> groupMembers(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId) {
    return ApiResponse.success("CHAT_GROUP_MEMBER_FOUND", "그룹 구성원 목록을 조회했습니다.",
        service.groupMembers(id(jwt), chatRoomId));
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

  // 숨긴 방을 목록에 다시 보이게 함(상단 검색 결과에서 숨긴 방을 열 때).
  @PostMapping("/unhide")
  public ApiResponse<Void> unhide(@AuthenticationPrincipal Jwt jwt, @RequestParam Long chatRoomId) {
    service.unhide(id(jwt), chatRoomId);
    return ApiResponse.success("CHAT_ROOM_UNHIDDEN", "채팅방을 다시 표시했습니다.", null);
  }

  // 상단 검색 [메시지]: 참여 중인 모든 방에서 키워드가 들어간 메시지. cursor는 이전 페이지의 nextCursor.
  @GetMapping("/search/message")
  public ApiResponse<ChatRoomResponse.MessageSearchPage> searchMessages(@AuthenticationPrincipal Jwt jwt,
      @RequestParam String keyword, @RequestParam(required = false) Long cursor) {
    return ApiResponse.success("CHAT_MESSAGE_SEARCHED", "메시지를 검색했습니다.",
        service.searchMessages(id(jwt), keyword, cursor));
  }

  // 상단 검색 [파일]: 참여 중인 모든 방에서 파일 이름에 키워드가 들어간 첨부.
  @GetMapping("/search/file")
  public ApiResponse<ChatRoomResponse.FileSearchPage> searchFiles(@AuthenticationPrincipal Jwt jwt,
      @RequestParam String keyword, @RequestParam(required = false) Long cursor) {
    return ApiResponse.success("CHAT_FILE_SEARCHED", "파일을 검색했습니다.",
        service.searchFiles(id(jwt), keyword, cursor));
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

  // 사진·파일 전송. 파일 하나가 메시지 하나가 됨.
  @PostMapping(value = "/message/attachment/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<ChatRoomResponse.Message> sendAttachment(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @RequestParam MultipartFile file,
      @RequestParam(required = false) Long replyToMessageId) {
    return ApiResponse.success("CHAT_ATTACHMENT_CREATED", "파일을 전송했습니다.",
        service.sendAttachment(id(jwt), chatRoomId, file, replyToMessageId));
  }

  // 방 상세의 사진·파일 모아보기. kind는 IMAGE 또는 FILE.
  @GetMapping("/message/attachment/list")
  public ApiResponse<ChatRoomResponse.AttachmentPage> attachments(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @RequestParam String kind, @RequestParam(required = false) Long cursor) {
    return ApiResponse.success("CHAT_ATTACHMENT_FOUND", "첨부 목록을 조회했습니다.",
        service.attachments(id(jwt), chatRoomId, kind, cursor));
  }

  // 첨부 내려받기. inline=true면 사진을 화면 표시용으로 보내고, 그 외에는 항상 저장(다운로드)용으로 보냄.
  @GetMapping("/message/attachment/download")
  public ResponseEntity<Resource> downloadAttachment(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long attachmentId, @RequestParam(defaultValue = "false") boolean inline) {
    ChatRoomService.AttachmentFile file = service.attachmentFile(id(jwt), attachmentId);
    ChatMessageAttachment attachment = file.attachment();
    boolean showInline = inline && attachment.getKind() == ChatAttachmentKind.IMAGE;
    // 일반 파일은 형식을 octet-stream으로 고정해 브라우저가 HTML 등으로 열어 실행하지 못하게 함.
    MediaType type = showInline ? MediaType.parseMediaType(attachment.getContentType())
        : MediaType.APPLICATION_OCTET_STREAM;
    ContentDisposition disposition = (showInline ? ContentDisposition.inline() : ContentDisposition.attachment())
        .filename(attachment.getOriginalName(), StandardCharsets.UTF_8).build();
    return ResponseEntity.ok()
        .contentType(type)
        .contentLength(attachment.getSize())
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        // 같은 파일은 내용이 바뀌지 않으므로 본인 브라우저에만 캐시.
        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
        .body(file.resource());
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

  // 메시지 전달. 선택한 방마다 새 메시지로 복사.
  @PostMapping("/message/forward")
  public ApiResponse<List<ChatRoomResponse.Message>> forwardMessage(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @RequestParam Long messageId, @Valid @RequestBody ChatRoomRequest.ForwardRequest r) {
    return ApiResponse.success("CHAT_MESSAGE_FORWARDED", "메시지를 전달했습니다.",
        service.forwardMessage(id(jwt), chatRoomId, messageId, r.targetChatRoomIds()));
  }

  // 공지 등록. 방의 공지 목록(최근 등록이 앞)을 돌려줌.
  @PostMapping("/notice/update")
  public ApiResponse<List<ChatRoomResponse.Notice>> updateNotice(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @RequestParam Long messageId) {
    return ApiResponse.success("CHAT_NOTICE_UPDATED", "공지를 등록했습니다.",
        service.updateNotice(id(jwt), chatRoomId, messageId));
  }

  // 공지 내리기(모든 참여자에게서 사라짐). 남은 공지 목록을 돌려줌.
  @PostMapping("/notice/remove")
  public ApiResponse<List<ChatRoomResponse.Notice>> removeNotice(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @RequestParam Long messageId) {
    return ApiResponse.success("CHAT_NOTICE_REMOVED", "공지를 내렸습니다.",
        service.removeNotice(id(jwt), chatRoomId, messageId));
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

  // 메시지에 리액션한 사람 목록(누가 어떤 이모지를 눌렀는지).
  @GetMapping("/message/reaction/member")
  public ApiResponse<List<ChatRoomResponse.ReactionMember>> reactionMembers(@AuthenticationPrincipal Jwt jwt,
      @RequestParam Long chatRoomId, @RequestParam Long messageId) {
    return ApiResponse.success("CHAT_REACTION_MEMBER_FOUND", "리액션 멤버를 조회했습니다.",
        service.reactionMembers(id(jwt), chatRoomId, messageId));
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
