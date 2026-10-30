package com.jhwjhw.naverworkscloneapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
// STOMP 연결 경로와 서버 내 간단 브로커를 설정.
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String allowedOrigin;
    private final StompJwtChannelInterceptor stompJwtChannelInterceptor;

    public WebSocketConfig(
            @Value("${app.cors.allowed-origin}") String allowedOrigin,
            StompJwtChannelInterceptor stompJwtChannelInterceptor) {
        this.allowedOrigin = allowedOrigin;
        this.stompJwtChannelInterceptor = stompJwtChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 공개 방 이벤트와 로그인 사용자 전용 이벤트를 분리해 전달.
        // 하트비트: 서버와 브라우저가 10초마다 "살아 있음" 신호를 주고받음. 신호가 끊긴 연결(인터넷 끊김·노트북 덮음)은
        // 서버가 닫고 연결 해제 이벤트를 발생시켜, 접속 상태가 오래 "온라인"으로 남지 않게 함.
        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setPoolSize(1);
        heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-");
        heartbeatScheduler.setDaemon(true);
        heartbeatScheduler.initialize();
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] { 10_000, 10_000 })
                .setTaskScheduler(heartbeatScheduler);
        registry.setUserDestinationPrefix("/user");
        // 클라이언트가 서버로 보내는 STOMP 명령 접두사.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Next.js 클라이언트가 연결할 WebSocket 엔드포인트.
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigin);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // CONNECT의 JWT 검증 후 인증된 사용자만 STOMP 명령을 처리.
        registration.interceptors(stompJwtChannelInterceptor);
    }
}
