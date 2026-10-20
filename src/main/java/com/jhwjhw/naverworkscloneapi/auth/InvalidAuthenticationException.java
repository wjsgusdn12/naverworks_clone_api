package com.jhwjhw.naverworkscloneapi.auth;

// 인증 실패를 한 종류로 통일해 ControllerAdvice에서 HTTP 401로 변환.
public class InvalidAuthenticationException extends RuntimeException {

    public InvalidAuthenticationException() {
        super("인증 정보가 올바르지 않습니다.");
    }
}
