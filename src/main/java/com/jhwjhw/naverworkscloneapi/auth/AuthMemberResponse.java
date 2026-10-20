package com.jhwjhw.naverworkscloneapi.auth;

// 로그인된 구성원 정보를 프론트에 전달하는 불변 응답 DTO.
public record AuthMemberResponse(
        Long membershipId,
        Long companyId,
        Long userAccountId,
        String email,
        String displayName,
        String role
) {
}
