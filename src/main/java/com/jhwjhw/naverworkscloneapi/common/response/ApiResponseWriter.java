package com.jhwjhw.naverworkscloneapi.common.response;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// Spring Security 필터 단계에서도 Controller와 동일한 JSON 응답을 쓰기 위한 공통 Writer.
@Component
@RequiredArgsConstructor
public class ApiResponseWriter {

    // Jackson ObjectMapper
    private final ObjectMapper objectMapper;

    // 오류 응답 쓰기
    public void writeFailure(
            HttpServletResponse response, // 응답 객체
            HttpStatus status, // 상태 코드
            String code, // 오류 코드
            String message // 오류 메시지
    ) throws IOException {
        response.setStatus(status.value()); // 상태 코드 설정
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // 콘텐츠 타입 설정
        response.setCharacterEncoding("UTF-8"); // 문자 인코딩 설정
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(code, message)); // 응답 본문 쓰기
    }
}
