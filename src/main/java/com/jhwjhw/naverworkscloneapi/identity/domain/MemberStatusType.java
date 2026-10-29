package com.jhwjhw.naverworkscloneapi.identity.domain;

// 구성원이 직접 고르는 상태. 접속 여부(온라인/오프라인)와는 별개로 프로필 배지에 표시됨.
public enum MemberStatusType {
    BUSY,           // 바쁨
    AWAY,           // 자리 비움
    OFF_WORK,       // 퇴근
    ABSENT,         // 부재
    VACATION,       // 휴가
    BUSINESS_TRIP,  // 출장
    SICK_LEAVE      // 병가
}
