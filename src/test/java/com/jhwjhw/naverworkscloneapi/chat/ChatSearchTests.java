package com.jhwjhw.naverworkscloneapi.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatAttachmentKind;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessage;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageAttachment;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageType;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoom;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomMember;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomType;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageAttachmentRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomMemberRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomRepository;
import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.domain.Role;
import com.jhwjhw.naverworkscloneapi.identity.domain.UserAccount;
import com.jhwjhw.naverworkscloneapi.identity.repository.CompanyRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.UserAccountRepository;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:chatSearchTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@Transactional
// 메시지 화면 상단 검색: 참여 중인 방(숨긴 방 포함)에서만 메시지·파일을 찾는지 검증. 모두 가상 데이터.
class ChatSearchTests {
    @Autowired private ChatRoomService service;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository accounts;
    @Autowired private MembershipRepository memberships;
    @Autowired private ChatRoomRepository rooms;
    @Autowired private ChatRoomMemberRepository participants;
    @Autowired private ChatMessageRepository messages;
    @Autowired private ChatMessageAttachmentRepository attachments;

    private Membership me;
    private Membership friend;
    private Membership stranger;
    private ChatRoom direct;

    @BeforeEach
    void setUp() {
        Company company = companies.save(new Company("가상 검색 회사"));
        me = member(company, "search-me@example.com", "검색하는 사람");
        friend = member(company, "search-friend@example.com", "대화 상대");
        stranger = member(company, "search-stranger@example.com", "남");
        direct = room(ChatRoomType.DIRECT, null, me, friend);
    }

    private Membership member(Company company, String email, String name) {
        UserAccount account = accounts.save(new UserAccount(email, "test-hash", name));
        return memberships.save(new Membership(company, account, null, Role.USER));
    }

    private ChatRoom room(ChatRoomType type, String name, Membership... members) {
        ChatRoom room = rooms.save(new ChatRoom(members[0].getCompany(), type, name, members[0]));
        for (Membership member : members) participants.save(new ChatRoomMember(room, member));
        return room;
    }

    private ChatMessage text(ChatRoom room, Membership sender, String content) {
        return messages.save(new ChatMessage(room, sender, content, ChatMessageType.TEXT));
    }

    private ChatMessageAttachment file(ChatRoom room, Membership sender, String name) {
        ChatMessage message = text(room, sender, name);
        return attachments.save(new ChatMessageAttachment(message, ChatAttachmentKind.FILE, name,
                "application/octet-stream", 1234, "fake-key-" + name, null, null));
    }

    @Test
    void findsMessagesOnlyInRoomsIJoinedIncludingHidden() {
        ChatMessage inDirect = text(direct, friend, "회의 자료 공유드립니다");
        ChatRoom hidden = room(ChatRoomType.GROUP, "숨긴 방", me, friend, stranger);
        ChatMessage inHidden = text(hidden, stranger, "다음 회의는 금요일");
        participants.findByChatRoomIdAndMembershipId(hidden.getId(), me.getId()).orElseThrow().hide();
        ChatRoom left = room(ChatRoomType.GROUP, "나간 방", me, friend, stranger);
        text(left, friend, "나간 방의 회의");
        participants.findByChatRoomIdAndMembershipId(left.getId(), me.getId()).orElseThrow().leave();
        ChatRoom others = room(ChatRoomType.GROUP, "남의 방", friend, stranger, member(me.getCompany(),
                "search-other@example.com", "다른 사람"));
        text(others, friend, "남의 방 회의");
        text(direct, friend, "회의 끝").deleteContent();
        messages.save(new ChatMessage(direct, me, "회의 안내(시스템)", ChatMessageType.SYSTEM));
        file(direct, friend, "회의록.pdf");

        var page = service.searchMessages(me.getId(), "  회의 ", null);

        // 최신순, 참여 중인 방(숨긴 방 포함)만. 나간 방·남의 방·삭제·시스템 안내·첨부 메시지는 제외.
        assertThat(page.items()).extracting(ChatRoomResponse.MessageSearchItem::messageId)
                .containsExactly(inHidden.getId(), inDirect.getId());
        assertThat(page.nextCursor()).isNull();
        var hiddenItem = page.items().getFirst();
        assertThat(hiddenItem.room().name()).isEqualTo("숨긴 방");
        assertThat(hiddenItem.room().hidden()).isTrue();
        assertThat(hiddenItem.senderName()).isEqualTo("남");
        // 1:1 방 이름은 상대 이름.
        assertThat(page.items().getLast().room().name()).isEqualTo("대화 상대");
        assertThat(page.items().getLast().room().hidden()).isFalse();
    }

    @Test
    void searchIsCaseInsensitiveAndTreatsWildcardsAsText() {
        ChatMessage percent = text(direct, friend, "진행률 100% 완료");
        text(direct, friend, "진행률 1000 완료");
        ChatMessage english = text(direct, friend, "Release NOTE 정리");

        assertThat(service.searchMessages(me.getId(), "0%", null).items())
                .extracting(ChatRoomResponse.MessageSearchItem::messageId).containsExactly(percent.getId());
        assertThat(service.searchMessages(me.getId(), "release note", null).items())
                .extracting(ChatRoomResponse.MessageSearchItem::messageId).containsExactly(english.getId());
        assertThat(service.searchMessages(me.getId(), "_", null).items()).isEmpty();
    }

    @Test
    void pagesByThirty() {
        IntStream.range(0, ChatRoomService.SEARCH_PAGE_SIZE + 5).forEach(index -> text(direct, friend, "반복 " + index));

        var first = service.searchMessages(me.getId(), "반복", null);
        assertThat(first.items()).hasSize(ChatRoomService.SEARCH_PAGE_SIZE);
        assertThat(first.nextCursor()).isNotNull();
        var second = service.searchMessages(me.getId(), "반복", first.nextCursor());
        assertThat(second.items()).hasSize(5);
        assertThat(second.nextCursor()).isNull();
        assertThat(second.items().getFirst().messageId()).isLessThan(first.items().getLast().messageId());
    }

    @Test
    void findsFilesByNameInJoinedRoomsOnly() {
        ChatMessageAttachment mine = file(direct, friend, "바코드_재고관리.xlsx");
        ChatRoom others = room(ChatRoomType.GROUP, "남의 방", friend, stranger, member(me.getCompany(),
                "search-other2@example.com", "다른 사람"));
        file(others, friend, "재고관리_남의것.xlsx");
        ChatMessageAttachment deleted = file(direct, friend, "재고관리_삭제됨.xlsx");
        deleted.getChatMessage().deleteContent();

        var page = service.searchFiles(me.getId(), "재고관리", null);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.attachment().id()).isEqualTo(mine.getId());
            assertThat(item.attachment().name()).isEqualTo("바코드_재고관리.xlsx");
            assertThat(item.room().name()).isEqualTo("대화 상대");
            assertThat(item.messageId()).isEqualTo(mine.getChatMessage().getId());
        });
    }

    @Test
    void rejectsBlankOrTooLongKeywordAndUnhidesRoom() {
        assertThatThrownBy(() -> service.searchMessages(me.getId(), "   ", null))
                .isInstanceOf(ChatException.class).hasMessage("검색어를 입력해 주세요.");
        assertThatThrownBy(() -> service.searchFiles(me.getId(), "가".repeat(ChatRoomService.SEARCH_KEYWORD_MAX + 1), null))
                .isInstanceOf(ChatException.class);

        ChatRoomMember mine = participants.findByChatRoomIdAndMembershipId(direct.getId(), me.getId()).orElseThrow();
        mine.hide();
        service.unhide(me.getId(), direct.getId());
        assertThat(mine.getHiddenAt()).isNull();
        // 참여하지 않은 사람은 숨김 해제 불가.
        assertThatThrownBy(() -> service.unhide(stranger.getId(), direct.getId()))
                .isInstanceOf(ChatException.class).hasMessage("채팅방 참가자가 아닙니다.");
    }
}
