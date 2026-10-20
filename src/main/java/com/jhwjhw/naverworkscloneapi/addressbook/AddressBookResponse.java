package com.jhwjhw.naverworkscloneapi.addressbook;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 주소록 API의 응답 DTO 모음. Entity를 직접 외부로 노출하지 않음.
public final class AddressBookResponse {

    private AddressBookResponse() {
        // 정적 DTO 묶음 클래스는 인스턴스 생성 금지.
    }

    public record OrganizationResponse(Long companyId, String companyName, List<DepartmentResponse> departments) {
    }

    public record DepartmentResponse(Long id, String name, boolean enabled) {
    }

    public record MemberPageResponse(List<MemberResponse> content, int page, int size, long totalElements, int totalPages) {
    }

    public record MemberResponse(
            Long membershipId,
            String displayName,
            String jobTitle,
            String jobGradeName,
            String departmentName,
            String companyName,
            String email,
            String phoneNumber,
            boolean enabled,
            MemberPresenceStatus presenceStatus,
            boolean favorite
    ) {
    }

    public record MemberDetailResponse(
            Long membershipId,
            String displayName,
            String jobTitle,
            String jobGradeName,
            String departmentName,
            String companyName,
            String email,
            String phoneNumber,
            LocalDate hiredAt,
            boolean enabled,
            MemberPresenceStatus presenceStatus,
            Instant awayUntil,
            boolean favorite
    ) {
    }

    public enum MemberPresenceStatus {
        ONLINE,
        OFFLINE,
        AWAY,
        INACTIVE
    }

    public record ContactGroupResponse(
            Long id,
            String name,
            long memberCount,
            String masterName,
            boolean member,
            boolean master,
            Instant createdAt
    ) {
    }
}
