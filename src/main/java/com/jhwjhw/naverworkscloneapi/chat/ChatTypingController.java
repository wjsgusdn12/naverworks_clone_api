package com.jhwjhw.naverworkscloneapi.chat;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import java.security.Principal;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomMember;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatTypingController {

    private final MembershipRepository membershipRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chatRoom/{chatRoomId}/typing")
    public void typing(@DestinationVariable Long chatRoomId, @Payload ChatTypingRequest request,
            Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken authentication)) {
            throw new AccessDeniedException("WebSocket 사용자 정보를 확인할 수 없습니다.");
        }
        Long membershipId = authentication.getToken().getClaim("membershipId");
        if (membershipId == null) {
            throw new AccessDeniedException("WebSocket 구성원 정보를 확인할 수 없습니다.");
        }

        Membership membership = membershipRepository.findWithUserAccountById(membershipId)
                .orElseThrow(() -> new ChatException("CHAT_MEMBER_NOT_FOUND", "구성원을 찾을 수 없습니다."));
        ChatTypingEvent event = new ChatTypingEvent("TYPING_CHANGED", chatRoomId, membershipId,
                membership.getUserAccount().getDisplayName(), request.typing());
        messagingTemplate.convertAndSend("/topic/chatRoom/" + chatRoomId, event);
        // 현재 방을 열지 않은 참여자도 자신의 전용 큐에서 목록 미리보기를 갱신.
        for (ChatRoomMember roomMember : chatRoomMemberRepository.findByChatRoomIdAndLeftAtIsNull(chatRoomId)) {
            messagingTemplate.convertAndSendToUser(
                    "membership:" + roomMember.getMembership().getId(),
                    "/queue/chatTyping",
                    event);
        }
        log.debug("Chat typing status broadcast. roomId={}, membershipId={}, typing={}", chatRoomId, membershipId,
                request.typing());
    }
}
