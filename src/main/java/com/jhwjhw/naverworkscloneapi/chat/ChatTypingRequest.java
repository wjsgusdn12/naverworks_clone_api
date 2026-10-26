package com.jhwjhw.naverworkscloneapi.chat;

// 입력 시작·종료만 전달하는 휘발성 WebSocket 요청.
public record ChatTypingRequest(boolean typing) {
}
