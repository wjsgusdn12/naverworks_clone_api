package com.jhwjhw.naverworkscloneapi.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AppAuthProperties(
        String issuer,
        String jwtSecretBase64
) {
}
