package com.jhwjhw.naverworkscloneapi.auth;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.domain.UserAccount;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.UserAccountRepository;
import com.jhwjhw.naverworkscloneapi.notification.NotificationService;
import com.jhwjhw.naverworkscloneapi.notification.domain.NotificationCategory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AuthService {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final UserAccountRepository userAccountRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AppAuthProperties appAuthProperties;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    // 로그인. userAgent는 "어떤 브라우저에서 로그인했는지" 시스템 알림에만 사용.
    @Transactional
    public AuthResult login(AuthRequest request, String userAgent) {
        // 계정 없음·비활성 계정·비밀번호 불일치를 같은 응답으로 처리해 계정 존재 여부를 숨김.
        UserAccount userAccount = userAccountRepository.findByEmail(normalizeEmail(request.email()))
                .filter(UserAccount::isEnabled)
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(InvalidAuthenticationException::new);

        // DB 유니크 제약으로 보장되는 단일 회사 소속을 인증 주체로 사용.
        Membership membership = membershipRepository.findByUserAccountId(userAccount.getId())
                .orElseThrow(InvalidAuthenticationException::new);

        // 비밀번호·토큰·이메일은 남기지 않고 인증 주체 식별자만 운영 로그에 기록.
        log.info("Login succeeded. membershipId={}", membership.getId());
        // 네이버웍스처럼 새 로그인을 시스템 알림으로 남겨 본인이 아닌 접속을 알아차릴 수 있게 함.
        notificationService.create(membership.getId(), NotificationCategory.SYSTEM, "로그인 알림",
                "새로운 브라우저(" + browserName(userAgent) + ")에서 로그인하였습니다.", null);
        return issueTokens(membership, request.rememberMe(), Instant.now().plus(appAuthProperties.refreshTokenExpiration()));
    }

    // 토큰 갱신
    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        RefreshToken refreshToken = findUsableRefreshToken(rawRefreshToken);
        // 토큰을 한 번 쓰면 즉시 폐기하는 Rotation 방식으로 탈취 토큰 재사용을 줄임.
        refreshToken.revoke(Instant.now());
        log.debug("Refresh token rotated. membershipId={}", refreshToken.getMembership().getId());
        // Rotation 후에도 최초 로그인에서 정한 전체 세션 만료 시각은 연장하지 않음.
        return issueTokens(refreshToken.getMembership(), refreshToken.isPersistentLogin(), refreshToken.getExpiresAt());
    }

    // 현재 로그인 구성원 정보 조회
    @Transactional(readOnly = true)
    public AuthMemberResponse getCurrentMember(Long membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(InvalidAuthenticationException::new);
        return toMemberResponse(membership);
    }

    // 로그아웃
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)).ifPresent(token -> {
            token.revoke(Instant.now());
            log.info("Logout succeeded. membershipId={}", token.getMembership().getId());
        });
    }

    // 리프레시 토큰 쿠키 생성
    public ResponseCookie createRefreshCookie(String rawRefreshToken, boolean persistentLogin, Instant sessionExpiresAt) {
        // HttpOnly라 브라우저 JavaScript로 읽을 수 없어 XSS 노출 범위를 줄임.
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie
                .from(REFRESH_TOKEN_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(appAuthProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth");

        if (persistentLogin) {
            // Rotation 뒤에도 최초 로그인 기준 남은 시간만큼만 영속 쿠키를 유지.
            cookieBuilder.maxAge(Duration.between(Instant.now(), sessionExpiresAt));
        }

        return cookieBuilder.build();
    }

    // 리프레시 토큰 쿠키 삭제
    public ResponseCookie deleteRefreshCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(appAuthProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    // 토큰 발급
    private AuthResult issueTokens(Membership membership, boolean persistentLogin, Instant sessionExpiresAt) {
        Instant now = Instant.now();
        // Access Token이 Refresh Token의 절대 만료 시각을 넘지 않게 제한.
        Instant configuredAccessTokenExpiresAt = now.plus(appAuthProperties.accessTokenExpiration());
        Instant accessTokenExpiresAt = configuredAccessTokenExpiresAt.isAfter(sessionExpiresAt)
                ? sessionExpiresAt
                : configuredAccessTokenExpiresAt;
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
                sessionExpiresAt,
                persistentLogin));

        AuthResponse response = new AuthResponse(
                accessToken,
                "Bearer",
                Duration.between(now, accessTokenExpiresAt).toSeconds(),
                sessionExpiresAt,
                toMemberResponse(membership));
        return new AuthResult(response, rawRefreshToken, persistentLogin, sessionExpiresAt);
    }

    // 사용 가능한 리프레시 토큰 조회
    private RefreshToken findUsableRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidAuthenticationException();
        }

        return refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .filter(token -> token.isUsableAt(Instant.now()))
                .orElseThrow(InvalidAuthenticationException::new);
    }

    // User-Agent 문자열에서 대표 브라우저 이름만 추출. 순서가 중요: Edge·Opera는 "Chrome"도 포함함.
    private static String browserName(String userAgent) {
        String agent = userAgent == null ? "" : userAgent;
        if (agent.contains("Edg/")) return "Edge";
        if (agent.contains("OPR/")) return "Opera";
        if (agent.contains("Whale/")) return "Whale";
        if (agent.contains("Firefox/")) return "Firefox";
        if (agent.contains("Chrome/")) return "Chrome";
        if (agent.contains("Safari/")) return "Safari";
        return "알 수 없는 브라우저";
    }

    // 이메일 정규화
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // 랜덤 토큰 생성
    private String createRandomToken() {
        // 예측하기 어려운 난수 48바이트를 URL에 안전한 문자열로 변환.
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // 토큰 해시
    private String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", exception);
        }
    }

    // 구성원 정보 응답 생성
    private AuthMemberResponse toMemberResponse(Membership membership) {
        UserAccount userAccount = membership.getUserAccount();
        return new AuthMemberResponse(
                membership.getId(),
                membership.getCompany().getId(),
                userAccount.getId(),
                userAccount.getEmail(),
                userAccount.getDisplayName(),
                membership.getRole().name());
    }

    // Controller에 응답 본문과 HttpOnly 쿠키 값을 함께 전달하는 서비스 내부 결과 객체.
    public record AuthResult(AuthResponse response, String refreshToken, boolean persistentLogin, Instant sessionExpiresAt) {
    }
}
