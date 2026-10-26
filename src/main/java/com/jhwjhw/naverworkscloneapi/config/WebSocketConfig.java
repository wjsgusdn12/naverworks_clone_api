package com.jhwjhw.naverworkscloneapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
// STOMP 연결 경로와 서버 내 간단 브로커를 설정.
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String allowedOrigin;

    public WebSocketConfig(@Value("${app.cors.allowed-origin}") String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버가 /topic으로 발행한 이벤트를 구독자에게 전달.
        registry.enableSimpleBroker("/topic");
        // 클라이언트가 서버로 보내는 STOMP 명령 접두사.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Next.js 클라이언트가 연결할 WebSocket 엔드포인트.
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigin);
    }
}
