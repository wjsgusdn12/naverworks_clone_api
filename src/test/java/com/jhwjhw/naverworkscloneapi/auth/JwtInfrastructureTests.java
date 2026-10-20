package com.jhwjhw.naverworkscloneapi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class JwtInfrastructureTests {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void issuedTokenCanBeVerifiedWithConfiguredSecret() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("naverworks-clone-api-test")
                .subject("membership:1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", "ADMIN")
                .build();

        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        // 발급 키와 검증 키가 같고 역할 클레임이 유지되는지 확인.
        assertThat(jwtDecoder.decode(tokenValue).getClaimAsString("role")).isEqualTo("ADMIN");
    }
}
