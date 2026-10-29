package com.jhwjhw.naverworkscloneapi.addressbook;

import com.jhwjhw.naverworkscloneapi.identity.domain.MemberStatusType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

// 주소록 API에서 사용하는 요청 DTO 모음.
public final class AddressBookRequest {

    private AddressBookRequest() {
        // 정적 DTO 묶음 클래스는 인스턴스 생성 금지.
    }

    public record FavoriteRequest(@NotNull @Positive Long targetMembershipId) {
    }

    public record SearchHistoryCreateRequest(@NotBlank @Size(max = 100) String keyword) {
    }

    // 상태 설정. statusType이 null이면 해제, statusUntil이 null이면 직접 해제할 때까지 유지.
    public record StatusRequest(MemberStatusType statusType, Instant statusUntil) {
    }

    public record ContactGroupCreateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull List<@Positive Long> membershipIds
    ) {
    }
}
