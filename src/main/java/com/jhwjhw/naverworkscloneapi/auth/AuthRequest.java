package com.jhwjhw.naverworkscloneapi.auth;

import jakarta.validation.constraints.NotBlank;

// 로그인 요청 본문. 아이디 또는 이메일 어느 쪽이든 식별자로 받음.
public record AuthRequest(
        // 아이디 또는 이메일
        @NotBlank String loginId,
        // 비밀번호
        @NotBlank String password,
        // 기억하기 여부
        boolean rememberMe
) {
}
