package com.jhwjhw.naverworkscloneapi.presence;

import com.jhwjhw.naverworkscloneapi.identity.domain.MemberStatusType;
import java.time.Instant;

// WebSocket 구독자에게 전달하는 구성원 접속 상태 DTO.
// online은 연결 여부, status는 화면 표시용 접속 상태(ONLINE/OFFLINE),
// statusType·statusUntil은 구성원이 고른 상태 설정(없으면 null).
public record PresenceRealtimeEvent(
        String type,
        Long membershipId,
        boolean online,
        String status,
        MemberStatusType statusType,
        Instant statusUntil
) {
}
