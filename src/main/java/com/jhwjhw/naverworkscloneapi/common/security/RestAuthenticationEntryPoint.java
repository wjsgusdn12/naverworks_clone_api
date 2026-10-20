package com.jhwjhw.naverworkscloneapi.common.security;

import com.jhwjhw.naverworkscloneapi.common.response.ApiResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

// 토큰이 없거나 만료·위조된 경우 Spring Security 단계에서 JSON 401 응답을 생성.
@Component
@RequiredArgsConstructor
@Slf4j
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiResponseWriter apiResponseWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        // Authorization 헤더 값은 기록하지 않고 요청 위치만 남김.
        log.warn("Authentication required. method={}, path={}", request.getMethod(), request.getRequestURI());
        apiResponseWriter.writeFailure(
                response,
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "로그인이 필요하거나 인증 정보가 만료되었습니다."
        );
    }
}
