package com.jhwjhw.naverworkscloneapi.auth;

import java.util.List;

// 가입 관련 응답 모음.
public final class SignupResponse {

    private SignupResponse() {
    }

    // 가입 성공: 로그인 세션과 복구 코드 원문. 복구 코드는 이 응답에서만 한 번 보여 줌.
    public record Created(AuthResponse session, String recoveryCode) {
    }

    // 가입 화면이 미리 보여 줄 체험 회사 정보(부서·직급 선택용).
    public record Options(String demoCompanyName, List<Option> departments, List<Option> jobGrades) {
    }

    public record Option(Long id, String name) {
    }

    // 복구 코드 재발급 결과. 역시 원문은 이 응답에서만.
    public record RecoveryCode(String recoveryCode) {
    }

    // 관리자가 보는 회사 초대 코드
    public record InviteCode(String companyName, String inviteCode) {
    }

    // 계정 찾기 1단계: 아이디는 일부만 가려서 보여 줌(예: ne***01)
    public record RecoverLookup(String maskedLoginId, String displayName) {
    }
}
