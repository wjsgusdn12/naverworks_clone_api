package com.jhwjhw.naverworkscloneapi.notification;

// DB 커밋 이후 받는 사람의 전용 큐로 전달할 새 알림.
public record NotificationCreatedEvent(Long membershipId, NotificationResponse.Item notification) {
}
