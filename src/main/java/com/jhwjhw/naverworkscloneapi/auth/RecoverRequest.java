package com.jhwjhw.naverworkscloneapi.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 계정 찾기 요청 모음. 둘 다 로그인 전에 복구 코드만으로 호출.
public final class RecoverRequest {

    private RecoverRequest() {
    }

    // 1단계: 복구 코드로 어느 계정인지 확인
    public record Lookup(@NotBlank @Size(max = 25) String recoveryCode) {
    }

    // 2단계: 같은 복구 코드로 새 비밀번호 설정
    public record Reset(@NotBlank @Size(max = 25) String recoveryCode,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
