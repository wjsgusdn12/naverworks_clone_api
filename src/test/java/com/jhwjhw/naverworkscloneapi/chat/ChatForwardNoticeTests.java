package com.jhwjhw.naverworkscloneapi.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoom;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomMember;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomType;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomMemberRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomRepository;
import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.domain.Role;
import com.jhwjhw.naverworkscloneapi.identity.domain.UserAccount;
import com.jhwjhw.naverworkscloneapi.identity.repository.CompanyRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:chatForwardNoticeTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
// 메시지 전달(복사·권한·라벨)과 방 공지(등록·교체·해제·삭제 시 자동 해제·실시간 이벤트) 검증.
class ChatForwardNoticeTests {
    @Autowired private ChatRoomService service;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository accounts;
    @Autowired private MembershipRepository memberships;
    @Autowired private ChatRoomRepository rooms;
    @Autowired private ChatRoomMemberRepository participants;
    @Autowired private ApplicationEvents events;

    private Membership author;
    private Membership other;
    private ChatRoom sourceRoom;
    private ChatRoom targetRoom;
    private ChatRoom strangerRoom;
    private Long messageId;

    @BeforeEach
    void setUp() {
        Company company = companies.save(new Company("전달·공지 테스트 " + UUID.randomUUID()));
        author = createMember(company, "작성자");
        other = createMember(company, "동료");
        Membership stranger = createMember(company, "외부인");
        sourceRoom = rooms.save(new ChatRoom(company, ChatRoomType.DIRECT, null, null));
        participants.save(new ChatRoomMember(sourceRoom, author));
        participants.save(new ChatRoomMember(sourceRoom, other));
        targetRoom = rooms.save(new ChatRoom(company, ChatRoomType.SELF, null, null));
        participants.save(new ChatRoomMember(targetRoom, author));
        strangerRoom = rooms.save(new ChatRoom(company, ChatRoomType.SELF, null, null));
        participants.save(new ChatRoomMember(strangerRoom, stranger));
        messageId = service.sendMessage(author.getId(), sourceRoom.getId(),
                new ChatRoomRequest.MessageCreateRequest("전달할 내용", List.of(), null)).id();
    }

    private Membership createMember(Company company, String name) {
        UserAccount account = accounts.save(new UserAccount(UUID.randomUUID() + "@example.com", "test-hash", name));
        return memberships.save(new Membership(company, account, null, Role.USER));
    }

    @Test
    void forwardCopiesContentIntoEachTargetRoomWithForwardedLabel() {
        // 같은 방을 두 번 골라도 한 번만 보냄.
        List<ChatRoomResponse.Message> copies = service.forwardMessage(other.getId(), sourceRoom.getId(), messageId,
                List.of(sourceRoom.getId(), sourceRoom.getId()));
        assertThat(copies).hasSize(1);
        assertThat(copies.getFirst().content()).isEqualTo("전달할 내용");
        assertThat(copies.getFirst().forwarded()).isTrue();
        assertThat(copies.getFirst().senderMembershipId()).isEqualTo(other.getId());
        assertThat(copies.getFirst().id()).isNotEqualTo(messageId);
        // 원본은 전달 표시가 없음.
        assertThat(service.messages(author.getId(), sourceRoom.getId(), null, null, null).messages().stream()
                .filter(item -> item.id().equals(messageId)).findFirst().orElseThrow().forwarded()).isFalse();
        assertThat(events.stream(ChatMessageCreatedEvent.class)).hasSize(2);
    }

    @Test
    void forwardIsRejectedForRoomsTheRequesterIsNotInAndForDeletedMessages() {
        assertThatThrownBy(() -> service.forwardMessage(author.getId(), sourceRoom.getId(), messageId,
                List.of(strangerRoom.getId())))
                .isInstanceOf(ChatException.class).hasMessageContaining("참가자가 아닙니다");
        service.deleteMessage(author.getId(), sourceRoom.getId(), messageId, 0L);
        assertThatThrownBy(() -> service.forwardMessage(author.getId(), sourceRoom.getId(), messageId,
                List.of(targetRoom.getId())))
                .isInstanceOf(ChatException.class);
    }

    @Test
    void anyMemberCanAddMultipleNoticesAndRemoveEachOne() {
        List<ChatRoomResponse.Notice> notices = service.updateNotice(other.getId(), sourceRoom.getId(), messageId);
        assertThat(notices).hasSize(1);
        assertThat(notices.getFirst().messageId()).isEqualTo(messageId);
        assertThat(notices.getFirst().content()).isEqualTo("전달할 내용");
        assertThat(notices.getFirst().senderName()).isEqualTo("작성자");
        assertThat(notices.getFirst().setByName()).isEqualTo("동료");
        assertThat(service.detail(author.getId(), sourceRoom.getId()).notices()).hasSize(1);
        // 같은 메시지를 다시 올려도 중복되지 않고 이벤트도 더 생기지 않음.
        assertThat(service.updateNotice(author.getId(), sourceRoom.getId(), messageId)).hasSize(1);

        // 다른 메시지를 올리면 교체가 아니라 추가되고, 최근 등록이 앞.
        Long second = service.sendMessage(author.getId(), sourceRoom.getId(),
                new ChatRoomRequest.MessageCreateRequest("두 번째 공지", List.of(), null)).id();
        List<ChatRoomResponse.Notice> two = service.updateNotice(author.getId(), sourceRoom.getId(), second);
        assertThat(two).extracting(ChatRoomResponse.Notice::messageId).containsExactly(second, messageId);

        // 하나만 내리면 나머지는 그대로.
        assertThat(service.removeNotice(other.getId(), sourceRoom.getId(), messageId))
                .extracting(ChatRoomResponse.Notice::messageId).containsExactly(second);
        // 이미 내린 공지를 다시 내려도 이벤트가 더 생기지 않음.
        service.removeNotice(other.getId(), sourceRoom.getId(), messageId);
        assertThat(service.removeNotice(other.getId(), sourceRoom.getId(), second)).isEmpty();
        assertThat(service.detail(author.getId(), sourceRoom.getId()).notices()).isEmpty();
        assertThat(events.stream(ChatNoticeChangedEvent.class)).hasSize(4);
        assertThat(events.stream(ChatNoticeChangedEvent.class).filter(event -> event.notices().isEmpty())).hasSize(1);
    }

    @Test
    void deletingNoticeMessageClearsNoticeAndSystemMessageCannotBeNotice() {
        service.updateNotice(author.getId(), sourceRoom.getId(), messageId);
        service.deleteMessage(author.getId(), sourceRoom.getId(), messageId, 0L);
        assertThat(service.detail(author.getId(), sourceRoom.getId()).notices()).isEmpty();
        assertThat(events.stream(ChatNoticeChangedEvent.class).filter(event -> event.notices().isEmpty())).hasSize(1);

        Long systemMessageId = service.messages(author.getId(), sourceRoom.getId(), null, null, null).messages().stream()
                .filter(item -> item.type().equals("SYSTEM")).findFirst().orElseThrow().id();
        assertThatThrownBy(() -> service.updateNotice(author.getId(), sourceRoom.getId(), systemMessageId))
                .isInstanceOf(ChatException.class).hasMessageContaining("시스템 안내");
    }
}
