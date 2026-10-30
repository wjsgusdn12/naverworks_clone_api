package com.jhwjhw.naverworkscloneapi.monitoring;

import com.jhwjhw.naverworkscloneapi.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 관리자 전용 API 대시보드. /api/v1/admin/** 는 SecurityConfig에서 ROLE_ADMIN만 허용.
@RestController
@RequiredArgsConstructor
public class ApiDashboardController {
    static final String PATH = "/api/v1/admin/apiDashboard";

    private final ApiDashboardService service;

    // range: HOUR(이번 시간)·DAY(오늘)·WEEK(이번 주)·MONTH(이번 달). 화면이 고른 탭 하나만 계산해 보냄.
    @GetMapping(PATH)
    public ApiResponse<ApiDashboardResponse> dashboard(
            @RequestParam(defaultValue = "DAY") ApiDashboardService.Range range) {
        return ApiResponse.success("API_DASHBOARD_FOUND", "API 대시보드를 조회했습니다.", service.dashboard(range));
    }
}
