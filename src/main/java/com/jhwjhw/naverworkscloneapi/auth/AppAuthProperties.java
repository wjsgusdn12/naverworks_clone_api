package com.jhwjhw.naverworkscloneapi.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

// application.yml의 app.auth 설정을 타입 안전하게 받는 객체.
@ConfigurationProperties(prefix = "app.auth")
public record AppAuthProperties(
        String issuer,
        String jwtSecretBase64,
        Duration accessTokenExpiration,
        Duration refreshTokenExpiration,
        boolean refreshCookieSecure
) {
}
