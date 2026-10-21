package com.jhwjhw.naverworkscloneapi.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 로그인 요청 본문. @Valid가 이메일 형식과 빈 값을 먼저 검사.
public record AuthRequest(
        // 이메일
        @NotBlank @Email String email,
        // 비밀번호
        @NotBlank String password,
        // 기억하기 여부
        boolean rememberMe
) {
}
