package com.jhwjhw.naverworkscloneapi.auth;

// 인증 실패를 한 종류로 통일해 ControllerAdvice에서 HTTP 401로 변환하기 위한 예외.
public class InvalidAuthenticationException extends RuntimeException {

    // 인증 실패 예외 생성
    public InvalidAuthenticationException() {
        super("인증 정보가 올바르지 않습니다.");
    }
}
