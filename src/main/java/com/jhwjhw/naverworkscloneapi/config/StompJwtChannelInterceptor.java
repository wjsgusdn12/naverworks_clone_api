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
            validateChatRoomSubscription(accessor);
        }
        return message;
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

    private void validateChatRoomSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new AccessDeniedException("WebSocket 구독 경로가 필요합니다.");
        }

        Matcher matcher = CHAT_ROOM_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            throw new AccessDeniedException("허용되지 않은 WebSocket 구독 경로입니다.");
        }

        if (!(accessor.getUser() instanceof JwtAuthenticationToken authentication)) {
            throw new AccessDeniedException("WebSocket 사용자 정보를 확인할 수 없습니다.");
        }

        Long membershipId = authentication.getToken().getClaim("membershipId");
        Long chatRoomId = Long.valueOf(matcher.group(1));
        // 활성 참여자만 해당 채팅방의 실시간 이벤트를 구독할 수 있음.
        if (membershipId == null || !chatRoomMemberRepository.existsActiveMember(chatRoomId, membershipId)) {
            throw new AccessDeniedException("채팅방 참여자만 실시간 메시지를 구독할 수 있습니다.");
        }
    }
}
