package com.jhwjhw.naverworkscloneapi.common.response;

import java.time.Instant;

// 모든 JSON API가 공유하는 응답 껍데기. data의 타입은 API마다 달라서 제네릭으로 선언.
public record ApiResponse<T>(
        // 성공 여부
        boolean success,
        // 오류 코드
        String code,
        // 오류 메시지
        String message,
        // 데이터
        T data,
        // 응답 시간
        Instant timestamp
) {

    // 정상 처리 결과를 공통 성공 형식으로 감싸는 생성 도우미.
    public static <T> ApiResponse<T> success(String code, String message, T data) {
        // 현재 시간을 응답 시간으로 설정
        return new ApiResponse<>(true, code, message, data, Instant.now());
    }

    // 오류 응답은 data 없이 코드와 사용자 안내 메시지만 반환.
    public static ApiResponse<Void> failure(String code, String message) {
        // 현재 시간을 응답 시간으로 설정
        return new ApiResponse<>(false, code, message, null, Instant.now());
    }
}
