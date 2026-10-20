package com.jhwjhw.naverworkscloneapi.auth;

// 로그인·재발급 성공 시 액세스 토큰과 화면 표시용 구성원 정보를 함께 반환.
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        AuthMemberResponse member
) {
}
