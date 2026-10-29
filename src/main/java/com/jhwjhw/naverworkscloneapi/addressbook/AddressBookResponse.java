package com.jhwjhw.naverworkscloneapi.addressbook;

import com.jhwjhw.naverworkscloneapi.identity.domain.MemberStatusType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 주소록 API의 응답 DTO 모음. Entity를 직접 외부로 노출하지 않음.
public final class AddressBookResponse {

    private AddressBookResponse() {
        // 정적 DTO 묶음 클래스는 인스턴스 생성 금지.
    }

    // memberCount는 사용 중인 계정 기준 회사 인원수.
    public record OrganizationResponse(Long companyId, String companyName, boolean currentCompany, long memberCount,
            List<DepartmentResponse> departments) {
    }

    public record DepartmentResponse(Long id, String name, boolean enabled) {
    }

    public record MemberPageResponse(List<MemberResponse> content, int page, int size, long totalElements, int totalPages) {
    }

    public record SearchHistoryResponse(Long id, String keyword, Instant updatedAt) {
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
            MemberStatusType statusType,
            Instant statusUntil,
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
            MemberStatusType statusType,
            Instant statusUntil,
            boolean favorite
    ) {
    }

    // 내 접속 상태와 상태 설정. statusType이 null이면 설정 안 함.
    public record MyStatusResponse(MemberPresenceStatus presenceStatus, MemberStatusType statusType,
            Instant statusUntil) {
    }

    // 접속 상태. 상태 설정(바쁨·휴가 등)은 statusType으로 따로 내려감.
    public enum MemberPresenceStatus {
        ONLINE,
        OFFLINE,
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
