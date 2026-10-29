package com.jhwjhw.naverworkscloneapi.addressbook;

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

    // 부재중은 시간(awayHours) 또는 종료 시각(awayUntil) 중 하나로 지정. 둘 다 있으면 종료 시각 우선.
    public record PresenceRequest(@NotNull PresenceStatus status, @Positive Integer awayHours, Instant awayUntil) {
    }

    public record ContactGroupCreateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull List<@Positive Long> membershipIds
    ) {
    }

    public enum PresenceStatus {
        ONLINE,
        AWAY
    }
}
