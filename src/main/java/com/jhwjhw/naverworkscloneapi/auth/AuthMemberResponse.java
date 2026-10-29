package com.jhwjhw.naverworkscloneapi.auth;

// 로그인된 구성원 정보를 프론트에 전달하는 불변 응답 DTO.
public record AuthMemberResponse(
        // 구성원 ID
        Long membershipId,
        // 회사 ID
        Long companyId,
        // 회사 이름
        String companyName,
        // 사용자 계정 ID
        Long userAccountId,
        // 로그인 아이디
        String loginId,
        // 이메일(선택 입력이라 비어 있을 수 있음)
        String email,
        // 표시 이름
        String displayName,
        // 역할
        String role
) {
}
