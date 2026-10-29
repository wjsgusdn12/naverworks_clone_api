package com.jhwjhw.naverworkscloneapi.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 계정 찾기 요청 모음. 아이디(또는 이메일)로는 아이디만 확인하고, 복구 코드로는 비밀번호까지 재설정.
public final class RecoverRequest {

    private RecoverRequest() {
    }

    // 1단계: 아이디(또는 이메일) 아니면 복구 코드, 둘 중 하나로 계정 확인
    public record Lookup(@Size(max = 255) String loginId,
            @Size(max = 25) String recoveryCode) {
    }

    // 2단계: 복구 코드로 새 비밀번호 설정. 아이디는 보냈을 때만 대조.
    public record Reset(@Size(max = 255) String loginId,
            @NotBlank @Size(max = 25) String recoveryCode,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
