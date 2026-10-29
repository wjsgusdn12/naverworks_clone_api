package com.jhwjhw.naverworkscloneapi.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 계정 찾기 요청 모음. 아이디(또는 이메일)와 복구 코드가 함께 맞아야 함.
public final class RecoverRequest {

    private RecoverRequest() {
    }

    // 1단계: 아이디·복구 코드로 계정 확인
    public record Lookup(@NotBlank @Size(max = 255) String loginId,
            @NotBlank @Size(max = 25) String recoveryCode) {
    }

    // 2단계: 같은 아이디·복구 코드로 새 비밀번호 설정
    public record Reset(@NotBlank @Size(max = 255) String loginId,
            @NotBlank @Size(max = 25) String recoveryCode,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
