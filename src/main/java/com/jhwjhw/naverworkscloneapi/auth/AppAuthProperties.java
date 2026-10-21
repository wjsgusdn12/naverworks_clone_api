package com.jhwjhw.naverworkscloneapi.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

// application.yml의 app.auth 설정을 타입 안전하게 받는 객체.
@ConfigurationProperties(prefix = "app.auth")
public record AppAuthProperties(
        // JWT 발급자
        String issuer,
        // JWT 시크릿 키
        String jwtSecretBase64,
        // 액세스 토큰 만료 시간
        Duration accessTokenExpiration,
        // 리프레시 토큰 만료 시간
        Duration refreshTokenExpiration,
        // 리프레시 쿠키 보안 설정
        boolean refreshCookieSecure
) {
}
