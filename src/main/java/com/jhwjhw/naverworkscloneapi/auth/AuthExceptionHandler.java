package com.jhwjhw.naverworkscloneapi.auth;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAuthentication() {
        // 계정 존재 여부를 노출하지 않는 공통 인증 실패 응답.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "code", "AUTHENTICATION_FAILED",
                "message", "이메일 또는 비밀번호를 확인해 주세요.",
                "timestamp", Instant.now().toString()
        ));
    }
}
