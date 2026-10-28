package com.jhwjhw.naverworkscloneapi.notification;

import com.jhwjhw.naverworkscloneapi.common.response.ApiResponse;
import com.jhwjhw.naverworkscloneapi.notification.domain.NotificationCategory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    // JWT에서 구성원 ID를 추출하는 메서드
    private Long id(Jwt jwt) {
        return jwt.getClaim("membershipId");
    }

    // 최근 30일 알림 조회. category를 생략하면 전체.
    @GetMapping
    public ApiResponse<List<NotificationResponse.Item>> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) NotificationCategory category) {
        return ApiResponse.success("NOTIFICATION_FOUND", "알림을 조회했습니다.", service.list(id(jwt), category));
    }

    // 알림 하나 읽음 처리
    @PostMapping("/read")
    public ApiResponse<NotificationResponse.Item> read(@AuthenticationPrincipal Jwt jwt,
            @RequestParam Long notificationId) {
        return ApiResponse.success("NOTIFICATION_READ", "알림을 읽음 처리했습니다.", service.read(id(jwt), notificationId));
    }

    // 모두 읽음 처리
    @PostMapping("/readAll")
    public ApiResponse<Integer> readAll(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("NOTIFICATION_READ_ALL", "모든 알림을 읽음 처리했습니다.", service.readAll(id(jwt)));
    }
}
