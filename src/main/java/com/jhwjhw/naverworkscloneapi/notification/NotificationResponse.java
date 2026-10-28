package com.jhwjhw.naverworkscloneapi.notification;

import com.jhwjhw.naverworkscloneapi.notification.domain.Notification;
import java.time.Instant;

public final class NotificationResponse {
    private NotificationResponse() {
    }

    // 알림 패널 카드 한 장에 필요한 정보.
    public record Item(Long id, String category, String title, String body, String linkPath, boolean read,
            Instant createdAt) {
        public static Item from(Notification notification) {
            return new Item(notification.getId(), notification.getCategory().name(), notification.getTitle(),
                    notification.getBody(), notification.getLinkPath(), notification.isRead(),
                    notification.getCreatedAt());
        }
    }
}
