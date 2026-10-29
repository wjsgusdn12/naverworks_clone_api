package com.jhwjhw.naverworkscloneapi.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 회사 옮기기 요청. 가입과 같은 세 갈래(체험 회사 / 새 회사 만들기 / 초대 코드)로 새 소속을 정함.
public record CompanyMoveRequest(
        @NotNull SignupRequest.JoinType joinType,
        // joinType=DEMO 일 때 선택
        Long departmentId,
        Long jobGradeId,
        // joinType=NEW_COMPANY 일 때
        @Size(max = 100) String companyName,
        // joinType=INVITE 일 때
        @Size(max = 20) String inviteCode
) {
}
