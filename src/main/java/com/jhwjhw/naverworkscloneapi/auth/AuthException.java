package com.jhwjhw.naverworkscloneapi.auth;

import lombok.Getter;

// 가입·복구처럼 사용자에게 이유를 알려 줘도 되는 인증 영역 오류. 400으로 응답.
@Getter
public class AuthException extends RuntimeException {

    private final String code;

    public AuthException(String code, String message) {
        super(message);
        this.code = code;
    }
}
