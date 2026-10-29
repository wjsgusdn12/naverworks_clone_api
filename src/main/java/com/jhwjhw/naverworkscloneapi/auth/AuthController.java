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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
// final 의존성 생성자는 Lombok이 자동 생성.
@RequiredArgsConstructor
public class AuthController {

        private final AuthService authService;

        // 로그인
        @PostMapping("/login")
        public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request,
                        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
                // 액세스 토큰은 응답 본문, 리프레시 토큰은 JavaScript가 읽을 수 없는 쿠키로 반환.
                AuthService.AuthResult result = authService.login(request, userAgent);
                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE,
                                                authService.createRefreshCookie(result.refreshToken(),
                                                                result.persistentLogin(), result.sessionExpiresAt()).toString())
                                .body(ApiResponse.success("AUTH_LOGIN_SUCCESS", "로그인에 성공했습니다.", result.response()));
        }

        // 가입 화면용 체험 회사 정보(로그인 전 접근)
        @GetMapping("/signup/options")
        public ApiResponse<SignupResponse.Options> signupOptions() {
                return ApiResponse.success("AUTH_SIGNUP_OPTIONS_FOUND", "가입 선택 항목을 조회했습니다.",
                                authService.signupOptions());
        }

        // 공개 가입. 성공하면 바로 로그인 세션과 복구 코드를 돌려줌.
        @PostMapping("/signup")
        public ResponseEntity<ApiResponse<SignupResponse.Created>> signup(@Valid @RequestBody SignupRequest request,
                        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
                AuthService.SignupResult result = authService.signup(request, userAgent);
                AuthService.AuthResult session = result.session();
                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE,
                                                authService.createRefreshCookie(session.refreshToken(),
                                                                session.persistentLogin(), session.sessionExpiresAt()).toString())
                                .body(ApiResponse.success("AUTH_SIGNUP_SUCCESS", "가입이 완료되었습니다.",
                                                new SignupResponse.Created(session.response(), result.recoveryCode())));
        }

        // 계정 찾기 1단계: 아이디(또는 이메일) 아니면 복구 코드로 계정 확인(로그인 전)
        @PostMapping("/recover/lookup")
        public ApiResponse<SignupResponse.RecoverLookup> recoverLookup(@Valid @RequestBody RecoverRequest.Lookup request) {
                return ApiResponse.success("AUTH_RECOVER_LOOKUP_SUCCESS", "계정을 확인했습니다.",
                                authService.recoverLookup(request.loginId(), request.recoveryCode()));
        }

        // 계정 찾기 2단계: 복구 코드로 비밀번호 재설정(로그인 전). 새 복구 코드를 돌려줌.
        @PostMapping("/recover/reset")
        public ApiResponse<SignupResponse.RecoveryCode> recoverReset(@Valid @RequestBody RecoverRequest.Reset request) {
                return ApiResponse.success("AUTH_RECOVER_RESET_SUCCESS", "비밀번호를 변경했습니다.",
                                authService.recoverReset(request.loginId(), request.recoveryCode(),
                                                request.newPassword()));
        }

        // 회사 옮기기. 회사 ID가 바뀌므로 새 토큰 쌍을 발급하고 리프레시 쿠키도 교체.
        @PostMapping("/company/move")
        public ResponseEntity<ApiResponse<AuthResponse>> moveCompany(@AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody CompanyMoveRequest request) {
                Long membershipId = jwt.getClaim("membershipId");
                AuthService.AuthResult result = authService.moveCompany(membershipId, request);
                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE,
                                                authService.createRefreshCookie(result.refreshToken(),
                                                                result.persistentLogin(), result.sessionExpiresAt()).toString())
                                .body(ApiResponse.success("AUTH_COMPANY_MOVED", "회사를 옮겼습니다.", result.response()));
        }

        // 복구 코드 재발급(로그인 후 내 프로필)
        @PostMapping("/recoveryCode/regenerate")
        public ApiResponse<SignupResponse.RecoveryCode> regenerateRecoveryCode(@AuthenticationPrincipal Jwt jwt) {
                Long membershipId = jwt.getClaim("membershipId");
                return ApiResponse.success("AUTH_RECOVERY_CODE_REGENERATED", "복구 코드를 새로 발급했습니다.",
                                authService.regenerateRecoveryCode(membershipId));
        }

        // 회사 초대 코드 조회(관리자). 처음 조회할 때 발급.
        @GetMapping("/company/inviteCode")
        public ApiResponse<SignupResponse.InviteCode> companyInviteCode(@AuthenticationPrincipal Jwt jwt) {
                Long membershipId = jwt.getClaim("membershipId");
                return ApiResponse.success("AUTH_INVITE_CODE_FOUND", "초대 코드를 조회했습니다.",
                                authService.companyInviteCode(membershipId, false));
        }

        // 회사 초대 코드 재발급(관리자). 이전 코드는 즉시 무효.
        @PostMapping("/company/inviteCode/regenerate")
        public ApiResponse<SignupResponse.InviteCode> regenerateInviteCode(@AuthenticationPrincipal Jwt jwt) {
                Long membershipId = jwt.getClaim("membershipId");
                return ApiResponse.success("AUTH_INVITE_CODE_REGENERATED", "초대 코드를 새로 발급했습니다.",
                                authService.companyInviteCode(membershipId, true));
        }

        // 토큰 갱신
        @PostMapping("/refresh")
        public ResponseEntity<ApiResponse<AuthResponse>> refresh(
                        @CookieValue(name = AuthService.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
                // 브라우저가 HttpOnly 쿠키를 자동 전송하면 새 토큰 쌍으로 교체.
                AuthService.AuthResult result = authService.refresh(refreshToken);
                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE,
                                                authService.createRefreshCookie(result.refreshToken(),
                                                                result.persistentLogin(), result.sessionExpiresAt()).toString())
                                .body(ApiResponse.success("AUTH_TOKEN_REFRESHED", "인증 토큰을 갱신했습니다.", result.response()));
        }

        // 로그아웃
        @PostMapping("/logout")
        public ResponseEntity<Void> logout(
                        @CookieValue(name = AuthService.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
                // DB 토큰을 폐기하고 브라우저 쿠키도 즉시 만료.
                authService.logout(refreshToken);
                return ResponseEntity.status(HttpStatus.NO_CONTENT)
                                .header(HttpHeaders.SET_COOKIE, authService.deleteRefreshCookie().toString())
                                .build();
        }

        // 현재 로그인 구성원 정보 조회
        @GetMapping("/me")
        public ApiResponse<AuthMemberResponse> me(@AuthenticationPrincipal Jwt jwt) {
                // Security가 검증한 JWT의 membershipId로 현재 로그인 구성원을 조회.
                Long membershipId = jwt.getClaim("membershipId");
                return ApiResponse.success(
                                "AUTH_MEMBER_FOUND",
                                "현재 로그인 구성원 정보를 조회했습니다.",
                                authService.getCurrentMember(membershipId));
        }
}
