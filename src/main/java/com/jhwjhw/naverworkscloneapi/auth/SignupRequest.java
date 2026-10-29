package com.jhwjhw.naverworkscloneapi.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 공개 가입 요청. 아이디·비밀번호·이름만 필수이고 나머지는 선택.
public record SignupRequest(
        // 아이디: 영문 소문자·숫자·._- 4~30자
        @NotBlank @Pattern(regexp = "^[a-z0-9._-]{4,30}$") String loginId,
        // 비밀번호: 8자 이상
        @NotBlank @Size(min = 8, max = 72) String password,
        // 표시 이름
        @NotBlank @Size(max = 50) String displayName,
        // 선택 연락처. 비워 두면 주소록에 표시하지 않음.
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phoneNumber,
        // 어느 회사에 들어갈지
        @NotNull JoinType joinType,
        // joinType=DEMO 일 때 체험 회사의 부서·직급(선택)
        Long departmentId,
        Long jobGradeId,
        // joinType=NEW_COMPANY 일 때 회사 이름
        @Size(max = 100) String companyName,
        // joinType=INVITE 일 때 초대 코드
        @Size(max = 20) String inviteCode
) {
    // 체험 회사 참여 / 새 회사 만들기(만든 사람이 대표) / 초대 코드로 참여
    public enum JoinType {
        DEMO, NEW_COMPANY, INVITE
    }
}
