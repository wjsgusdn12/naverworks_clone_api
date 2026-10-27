package com.jhwjhw.naverworkscloneapi.config;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
// STOMP CONNECT 헤더의 Access Token을 검증해 WebSocket 사용자 정보를 등록.
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern CHAT_ROOM_TOPIC = Pattern.compile("^/topic/chatRoom/(\\d+)$");
    private static final Pattern PRESENCE_TOPIC = Pattern.compile("^/topic/presence$");
    private static final Pattern USER_TYPING_QUEUE = Pattern.compile("^/user/queue/chatTyping$");
    private static final Pattern CHAT_ROOM_TYPING = Pattern.compile("^/app/chatRoom/(\\d+)/typing$");

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor));
            return message;
        }

        // CONNECT가 끝나지 않은 세션은 구독·메시지 전송을 허용하지 않음.
        if (!StompCommand.DISCONNECT.equals(accessor.getCommand()) && accessor.getUser() == null) {
            throw new AccessDeniedException("WebSocket 인증이 필요합니다.");
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            if (USER_TYPING_QUEUE.matcher(accessor.getDestination() == null ? "" : accessor.getDestination()).matches()) {
                validateUserQueueAccess(accessor);
            } else if (PRESENCE_TOPIC.matcher(accessor.getDestination() == null ? "" : accessor.getDestination()).matches()) {
                // 인증된 사용자는 전체 구성원의 온라인 상태 이벤트를 구독.
            } else {
                validateChatRoomAccess(accessor, CHAT_ROOM_TOPIC, "구독");
            }
        }
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            validateChatRoomAccess(accessor, CHAT_ROOM_TYPING, "입력 상태 전송");
        }
        return message;
    }

    private void validateUserQueueAccess(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new AccessDeniedException("WebSocket 구독 경로가 필요합니다.");
        }
        if (!USER_TYPING_QUEUE.matcher(destination).matches()) {
            return;
        }
        if (!(accessor.getUser() instanceof JwtAuthenticationToken authentication)) {
            throw new AccessDeniedException("WebSocket 사용자 정보를 확인할 수 없습니다.");
        }
        // /user 경로는 Spring이 현재 인증 주체의 전용 큐로 변환.
        if (authentication.getToken().getClaim("membershipId") == null) {
            throw new AccessDeniedException("WebSocket 구성원 정보를 확인할 수 없습니다.");
        }
    }

    private JwtAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        String authorization = firstHeader(accessor.getNativeHeader(AUTHORIZATION_HEADER));
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("WebSocket Access Token이 필요합니다.");
        }

        Jwt jwt = jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()));
        JwtAuthenticationToken authentication = (JwtAuthenticationToken) jwtAuthenticationConverter.convert(jwt);
        if (authentication == null) {
            throw new AccessDeniedException("WebSocket 사용자 인증에 실패했습니다.");
        }
        return authentication;
    }

    private String firstHeader(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private void validateChatRoomAccess(StompHeaderAccessor accessor, Pattern destinationPattern, String action) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new AccessDeniedException("WebSocket " + action + " 경로가 필요합니다.");
        }

        Matcher matcher = destinationPattern.matcher(destination);
        if (!matcher.matches()) {
            throw new AccessDeniedException("허용되지 않은 WebSocket " + action + " 경로입니다.");
        }

        if (!(accessor.getUser() instanceof JwtAuthenticationToken authentication)) {
            throw new AccessDeniedException("WebSocket 사용자 정보를 확인할 수 없습니다.");
        }

        Long membershipId = authentication.getToken().getClaim("membershipId");
        Long chatRoomId = Long.valueOf(matcher.group(1));
        // 활성 참여자만 해당 채팅방의 실시간 이벤트를 송수신할 수 있음.
        if (membershipId == null || !chatRoomMemberRepository.existsActiveMember(chatRoomId, membershipId)) {
            throw new AccessDeniedException("채팅방 참여자만 실시간 이벤트를 사용할 수 있습니다.");
        }
    }
}
