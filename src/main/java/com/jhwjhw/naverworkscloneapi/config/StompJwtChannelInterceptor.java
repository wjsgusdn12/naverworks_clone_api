package com.jhwjhw.naverworkscloneapi.config;

import java.util.List;
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

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

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
}
