package com.jhwjhw.naverworkscloneapi.notification;

// 알림 도메인 규칙 위반을 공통 오류 응답으로 전달.
public class NotificationException extends RuntimeException {
    private final String code;

    public NotificationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
