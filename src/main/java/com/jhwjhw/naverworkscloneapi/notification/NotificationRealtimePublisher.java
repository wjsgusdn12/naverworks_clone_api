package com.jhwjhw.naverworkscloneapi.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    // 커밋이 끝난 알림만 받는 사람의 전용 큐(/user/queue/notification)로 전달.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreated(NotificationCreatedEvent event) {
        messagingTemplate.convertAndSendToUser("membership:" + event.membershipId(), "/queue/notification",
                new NotificationRealtimeEvent("NOTIFICATION_CREATED", event.notification()));
        log.debug("Realtime notification published. membershipId={}, notificationId={}", event.membershipId(),
                event.notification().id());
    }
}
