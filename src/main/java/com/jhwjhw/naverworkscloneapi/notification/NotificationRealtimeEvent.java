package com.jhwjhw.naverworkscloneapi.notification;

// WebSocket으로 내려보내는 알림 이벤트. type은 프론트가 분기하는 기준.
public record NotificationRealtimeEvent(String type, NotificationResponse.Item notification) {
}
