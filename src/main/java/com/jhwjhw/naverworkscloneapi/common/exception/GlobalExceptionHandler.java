package com.jhwjhw.naverworkscloneapi.common.exception;

import com.jhwjhw.naverworkscloneapi.auth.InvalidAuthenticationException;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookException;
import com.jhwjhw.naverworkscloneapi.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Controller 안에서 발생한 예외를 API 공통 형식으로 변환.
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        // 주소록 관련 예외 처리
        @ExceptionHandler(AddressBookException.class)
        public ResponseEntity<ApiResponse<Void>> handleAddressBookException(AddressBookException exception,
                        HttpServletRequest request) {
                log.warn("Address book request rejected. code={}, method={}, path={}", exception.getCode(),
                                request.getMethod(), request.getRequestURI());
                return ResponseEntity.badRequest()
                                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
        }

        // 인증 실패 예외 처리
        @ExceptionHandler(InvalidAuthenticationException.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidAuthentication(HttpServletRequest request) {
                // 계정 존재 여부를 노출하지 않는 공통 인증 실패 응답.
                log.warn("Authentication failed. method={}, path={}", request.getMethod(), request.getRequestURI());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.failure("AUTHENTICATION_FAILED", "이메일 또는 비밀번호를 확인해 주세요."));
        }

        // 유효성 검증 실패 예외 처리
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleValidationFailure(
                        MethodArgumentNotValidException exception,
                        HttpServletRequest request) {
                // 세부 검증 규칙은 서버 로그로 남기고, 클라이언트에는 일관된 입력 오류만 제공.
                log.warn(
                                "Validation failed. method={}, path={}, fieldErrorCount={}",
                                request.getMethod(),
                                request.getRequestURI(),
                                exception.getBindingResult().getErrorCount());
                return ResponseEntity.badRequest()
                                .body(ApiResponse.failure("VALIDATION_FAILED", "요청 값을 다시 확인해 주세요."));
        }

        // JSON 문법 오류 처리
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(
                        HttpMessageNotReadableException exception,
                        HttpServletRequest request) {
                // JSON 문법 오류와 요청 본문 타입 불일치를 같은 400 응답으로 처리.
                log.warn("Malformed request body. method={}, path={}", request.getMethod(), request.getRequestURI());
                return ResponseEntity.badRequest()
                                .body(ApiResponse.failure("MALFORMED_REQUEST", "요청 본문 형식을 다시 확인해 주세요."));
        }

        // 예상치 못한 예외 처리
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
                // 상세 원인은 서버 로그에만 남겨 내부 구현 정보가 외부에 노출되지 않도록 함.
                log.error("처리하지 못한 서버 오류가 발생했습니다.", exception);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.failure("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
        }
}
