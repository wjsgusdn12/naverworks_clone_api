package com.jhwjhw.naverworkscloneapi.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

// 모든 /api 요청이 지나가는 "관문". 걸린 시간과 결과(상태 코드)를 재서 집계 상자에 넣음.
// 보안 필터보다 먼저 감싸도록 순서를 앞에 둬서, 로그인 실패(401)·권한 없음(403)도 함께 셈.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class ApiMetricsFilter extends OncePerRequestFilter {
    // 컨트롤러까지 가기 전에 끝난 요청(인증 실패 등)은 경로 패턴을 알 수 없어 한 묶음으로 셈.
    static final String UNMATCHED = "(컨트롤러 도달 전 종료)";

    private final ApiMetricsRecorder recorder;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 대시보드 자신의 30초 주기 조회는 숫자를 부풀리므로 빼고, 브라우저 사전 확인(OPTIONS)도 뺌.
        return !path.startsWith("/api/") || path.startsWith(ApiDashboardController.PATH)
                || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            // "/api/v1/chatRoom/message/update/17"처럼 번호가 다른 주소를 "{messageId}" 하나로 묶어서 셈.
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String endpoint = pattern == null ? UNMATCHED : request.getMethod() + " " + pattern;
            recorder.record(endpoint, failed ? 500 : response.getStatus(), durationMs, Instant.now());
        }
    }
}
