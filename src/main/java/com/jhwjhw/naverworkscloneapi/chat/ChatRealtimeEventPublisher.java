package com.jhwjhw.naverworkscloneapi.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRealtimeEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMessageCreated(ChatMessageCreatedEvent event) {
        String destination = "/topic/chatRoom/" + event.chatRoomId();
        messagingTemplate.convertAndSend(destination,
                new ChatRealtimeEvent("MESSAGE_CREATED", event.chatRoomId(), event.message()));
        log.debug("Realtime chat message published. roomId={}, messageId={}", event.chatRoomId(),
                event.message().id());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMessageChanged(ChatMessageChangedEvent event) {
        messagingTemplate.convertAndSend("/topic/chatRoom/" + event.chatRoomId(),
                new ChatRealtimeEvent(event.type(), event.chatRoomId(), event.message()));
        log.debug("Realtime message change published. type={}, roomId={}, messageId={}, version={}",
                event.type(), event.chatRoomId(), event.message().id(), event.message().version());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishReactionChanged(ChatReactionChangedEvent event) {
        messagingTemplate.convertAndSend("/topic/chatRoom/" + event.chatRoomId(),
                new ChatReactionRealtimeEvent("REACTION_UPDATED", event.chatRoomId(), event.messageId(),
                        event.emoji(), event.membershipId(), event.action()));
        log.debug("Realtime reaction change published. roomId={}, messageId={}, emoji={}, membershipId={}, action={}",
                event.chatRoomId(), event.messageId(), event.emoji(), event.membershipId(), event.action());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMessageReadUpdated(ChatMessageReadUpdatedEvent event) {
        messagingTemplate.convertAndSend("/topic/chatRoom/" + event.chatRoomId(),
                new ChatReadRealtimeEvent("READ_UPDATED", event.chatRoomId(), event.readerMembershipId(),
                        event.lastReadMessageId()));
        log.debug("Realtime chat read state published. roomId={}, readerId={}, lastReadMessageId={}",
                event.chatRoomId(), event.readerMembershipId(), event.lastReadMessageId());
    }
}
