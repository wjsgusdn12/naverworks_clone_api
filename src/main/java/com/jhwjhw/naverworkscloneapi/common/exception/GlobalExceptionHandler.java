package com.jhwjhw.naverworkscloneapi.common.exception;

import com.jhwjhw.naverworkscloneapi.auth.InvalidAuthenticationException;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookException;
import com.jhwjhw.naverworkscloneapi.chat.ChatException;
import com.jhwjhw.naverworkscloneapi.common.response.ApiResponse;
import com.jhwjhw.naverworkscloneapi.notification.NotificationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Controller 안에서 발생한 예외를 API 공통 형식으로 변환.
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        // 채팅 도메인 규칙 위반 처리
        @ExceptionHandler(ChatException.class)
        public ResponseEntity<ApiResponse<Void>> handleChatException(ChatException exception, HttpServletRequest request) {
                log.warn("Chat request rejected. code={}, method={}, path={}", exception.getCode(), request.getMethod(),
                                request.getRequestURI());
                HttpStatus status = switch (exception.getCode()) {
                        case "CHAT_MESSAGE_AUTHOR_REQUIRED", "CHAT_MESSAGE_EDIT_EXPIRED", "CHAT_MESSAGE_DELETE_EXPIRED",
                                        "CHAT_REACTION_EXPIRED" ->
                                HttpStatus.FORBIDDEN;
                        case "CHAT_MESSAGE_CONFLICT", "CHAT_MESSAGE_ALREADY_DELETED" -> HttpStatus.CONFLICT;
                        default -> HttpStatus.BAD_REQUEST;
                };
                return ResponseEntity.status(status).body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
        }

        @ExceptionHandler(OptimisticLockingFailureException.class)
        public ResponseEntity<ApiResponse<Void>> handleConcurrentUpdate(OptimisticLockingFailureException exception,
                        HttpServletRequest request) {
                log.warn("Concurrent update rejected. method={}, path={}", request.getMethod(), request.getRequestURI());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure("CHAT_MESSAGE_CONFLICT",
                                "다른 화면에서 메시지가 변경됐습니다. 최신 내용을 확인해 주세요."));
        }

        // 알림 관련 예외 처리
        @ExceptionHandler(NotificationException.class)
        public ResponseEntity<ApiResponse<Void>> handleNotificationException(NotificationException exception,
                        HttpServletRequest request) {
                log.warn("Notification request rejected. code={}, method={}, path={}", exception.getCode(),
                                request.getMethod(), request.getRequestURI());
                HttpStatus status = "NOTIFICATION_NOT_FOUND".equals(exception.getCode()) ? HttpStatus.NOT_FOUND
                                : HttpStatus.BAD_REQUEST;
                return ResponseEntity.status(status).body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
        }

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
