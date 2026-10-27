package com.jhwjhw.naverworkscloneapi.chat.domain;

// 채팅방별 수신 기준. MENTION은 나를 직접 멘션한 메시지만 알림을 보낸다.
public enum ChatNotificationMode {
    ALL,
    MENTION,
    NONE
}
