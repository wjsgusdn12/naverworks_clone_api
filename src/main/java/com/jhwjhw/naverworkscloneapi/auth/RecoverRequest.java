package com.jhwjhw.naverworkscloneapi.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 계정 찾기 요청 모음. 본인 정보(이름·이메일·전화번호)가 모두 맞거나 복구 코드가 맞으면 비밀번호까지 재설정.
public final class RecoverRequest {

    private RecoverRequest() {
    }

    // 1단계: 본인 정보 세 가지 아니면 복구 코드, 둘 중 하나로 계정 확인
    public record Lookup(@Size(max = 100) String displayName,
            @Size(max = 255) String email,
            @Size(max = 30) String phoneNumber,
            @Size(max = 25) String recoveryCode) {
    }

    // 2단계: 1단계와 같은 정보로 한 번 더 확인한 뒤 새 비밀번호 설정
    public record Reset(@Size(max = 100) String displayName,
            @Size(max = 255) String email,
            @Size(max = 30) String phoneNumber,
            @Size(max = 25) String recoveryCode,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
