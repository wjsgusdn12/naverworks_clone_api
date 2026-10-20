package com.jhwjhw.naverworkscloneapi.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 로그인 요청 본문. @Valid가 이메일 형식과 빈 값을 먼저 검사.
public record AuthRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        // false면 브라우저를 닫을 때 만료되는 세션 쿠키로 발급.
        boolean rememberMe
) {
}
