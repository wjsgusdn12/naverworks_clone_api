package com.jhwjhw.naverworkscloneapi.auth;

// 로그인·재발급 성공 시 액세스 토큰과 화면 표시용 구성원 정보를 함께 반환.
public record AuthResponse(
        // 액세스 토큰
        String accessToken,
        // 토큰 타입
        String tokenType,
        // 만료 시간
        long expiresIn,
        // 구성원 정보
        AuthMemberResponse member
) {
}
