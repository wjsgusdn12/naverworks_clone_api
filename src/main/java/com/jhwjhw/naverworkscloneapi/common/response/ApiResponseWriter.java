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

    private final ObjectMapper objectMapper;

    public void writeFailure(
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(code, message));
    }
}
