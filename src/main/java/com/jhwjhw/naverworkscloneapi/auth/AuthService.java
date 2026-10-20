package com.jhwjhw.naverworkscloneapi.auth;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.domain.UserAccount;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// final 의존성 생성자는 Lombok이 자동 생성.
@RequiredArgsConstructor
public class AuthService {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final UserAccountRepository userAccountRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AppAuthProperties appAuthProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthResult login(AuthRequest request) {
        // 계정 없음·비활성 계정·비밀번호 불일치를 같은 응답으로 처리해 계정 존재 여부를 숨김.
        UserAccount userAccount = userAccountRepository.findByEmail(normalizeEmail(request.email()))
                .filter(UserAccount::isEnabled)
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(InvalidAuthenticationException::new);

        // DB 유니크 제약으로 보장되는 단일 회사 소속을 인증 주체로 사용.
        Membership membership = membershipRepository.findByUserAccountId(userAccount.getId())
                .orElseThrow(InvalidAuthenticationException::new);

        return issueTokens(membership);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        RefreshToken refreshToken = findUsableRefreshToken(rawRefreshToken);
        // 토큰을 한 번 쓰면 즉시 폐기하는 Rotation 방식으로 탈취 토큰 재사용을 줄임.
        refreshToken.revoke(Instant.now());
        return issueTokens(refreshToken.getMembership());
    }

    @Transactional(readOnly = true)
    public AuthMemberResponse getCurrentMember(Long membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(InvalidAuthenticationException::new);
        return toMemberResponse(membership);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    public ResponseCookie createRefreshCookie(String rawRefreshToken) {
        // HttpOnly라 브라우저 JavaScript로 읽을 수 없어 XSS 노출 범위를 줄임.
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(appAuthProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(appAuthProperties.refreshTokenExpiration())
                .build();
    }

    public ResponseCookie deleteRefreshCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(appAuthProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    private AuthResult issueTokens(Membership membership) {
        Instant now = Instant.now();
        Instant accessTokenExpiresAt = now.plus(appAuthProperties.accessTokenExpiration());
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                .issuer(appAuthProperties.issuer())
                .subject("membership:" + membership.getId())
                .issuedAt(now)
                .expiresAt(accessTokenExpiresAt)
                // 이후 API 인가 시 현재 사용자와 회사·역할을 식별하는 최소 클레임.
                .claim("membershipId", membership.getId())
                .claim("companyId", membership.getCompany().getId())
                .claim("role", membership.getRole().name())
                .build())).getTokenValue();

        // DB에는 원문이 아닌 해시만 저장하고, 원문은 지금 응답 쿠키에만 사용.
        String rawRefreshToken = createRandomToken();
        refreshTokenRepository.save(new RefreshToken(
                UUID.randomUUID(),
                membership,
                hashToken(rawRefreshToken),
                now.plus(appAuthProperties.refreshTokenExpiration())
        ));

        AuthResponse response = new AuthResponse(
                accessToken,
                "Bearer",
                appAuthProperties.accessTokenExpiration().toSeconds(),
                toMemberResponse(membership)
        );
        return new AuthResult(response, rawRefreshToken);
    }

    private RefreshToken findUsableRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidAuthenticationException();
        }

        return refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .filter(token -> token.isUsableAt(Instant.now()))
                .orElseThrow(InvalidAuthenticationException::new);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String createRandomToken() {
        // 예측하기 어려운 난수 48바이트를 URL에 안전한 문자열로 변환.
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", exception);
        }
    }

    private AuthMemberResponse toMemberResponse(Membership membership) {
        UserAccount userAccount = membership.getUserAccount();
        return new AuthMemberResponse(
                membership.getId(),
                membership.getCompany().getId(),
                userAccount.getId(),
                userAccount.getEmail(),
                userAccount.getDisplayName(),
                membership.getRole().name()
        );
    }

    // Controller에 응답 본문과 HttpOnly 쿠키 값을 함께 전달하는 서비스 내부 결과 객체.
    public record AuthResult(AuthResponse response, String refreshToken) {
    }
}
