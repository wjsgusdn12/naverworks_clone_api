package com.jhwjhw.naverworkscloneapi.common.security;

import com.jhwjhw.naverworkscloneapi.common.response.ApiResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

// 로그인은 했지만 현재 역할로 접근할 수 없을 때 JSON 403 응답을 생성.
@Component
@RequiredArgsConstructor
@Slf4j
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiResponseWriter apiResponseWriter;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        // 권한 오류의 원인은 남기되 토큰·개인정보는 로그에 포함하지 않음.
        log.warn("Access denied. method={}, path={}", request.getMethod(), request.getRequestURI());
        apiResponseWriter.writeFailure(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "접근 권한이 없습니다.");
    }
}
