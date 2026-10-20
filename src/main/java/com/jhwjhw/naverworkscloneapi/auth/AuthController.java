package com.jhwjhw.naverworkscloneapi.auth;

import com.jhwjhw.naverworkscloneapi.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
// final 의존성 생성자는 Lombok이 자동 생성.
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        // 액세스 토큰은 응답 본문, 리프레시 토큰은 JavaScript가 읽을 수 없는 쿠키로 반환.
        AuthService.AuthResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authService.createRefreshCookie(result.refreshToken(), result.persistentLogin()).toString())
                .body(ApiResponse.success("AUTH_LOGIN_SUCCESS", "로그인에 성공했습니다.", result.response()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = AuthService.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken
    ) {
        // 브라우저가 HttpOnly 쿠키를 자동 전송하면 새 토큰 쌍으로 교체.
        AuthService.AuthResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authService.createRefreshCookie(result.refreshToken(), result.persistentLogin()).toString())
                .body(ApiResponse.success("AUTH_TOKEN_REFRESHED", "인증 토큰을 갱신했습니다.", result.response()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthService.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken
    ) {
        // DB 토큰을 폐기하고 브라우저 쿠키도 즉시 만료.
        authService.logout(refreshToken);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, authService.deleteRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public ApiResponse<AuthMemberResponse> me(@AuthenticationPrincipal Jwt jwt) {
        // Security가 검증한 JWT의 membershipId로 현재 로그인 구성원을 조회.
        Long membershipId = jwt.getClaim("membershipId");
        return ApiResponse.success(
                "AUTH_MEMBER_FOUND",
                "현재 로그인 구성원 정보를 조회했습니다.",
                authService.getCurrentMember(membershipId)
        );
    }
}
