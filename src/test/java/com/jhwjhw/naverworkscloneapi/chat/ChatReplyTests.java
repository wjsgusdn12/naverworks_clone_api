package com.jhwjhw.naverworkscloneapi.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessage;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageType;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoom;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomMember;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomType;
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
import jakarta.persistence.EntityManager;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:chatReplyTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@Transactional
// 기존 PostgreSQL 전용 마이그레이션과 분리한 H2 DB에서 JPA 저장·권한·원문 페이지 연결 검증.
class ChatReplyTests {
    @Autowired private ChatRoomService service;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository accounts;
    @Autowired private MembershipRepository memberships;
    @Autowired private ChatRoomRepository rooms;
    @Autowired private ChatRoomMemberRepository participants;
    @Autowired private ChatMessageRepository messages;
    @Autowired private EntityManager entityManager;

    private Membership sender;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        Company company = companies.save(new Company("답장 테스트 회사"));
        UserAccount account = accounts.save(new UserAccount("reply-test@example.com", "test-hash", "원문 작성자"));
        sender = memberships.save(new Membership(company, account, null, Role.USER));
        room = rooms.save(new ChatRoom(company, ChatRoomType.SELF, null, sender));
        participants.save(new ChatRoomMember(room, sender));
    }

    @Test
    void replyKeepsOriginalSnapshotAfterReloadAndAuthorRename() {
        ChatMessage original = messages.save(new ChatMessage(room, sender, "첫째 줄\n둘째 줄", ChatMessageType.TEXT));
        var response = service.sendMessage(sender.getId(), room.getId(),
                new ChatRoomRequest.MessageCreateRequest("답장 본문", List.of(), original.getId()));
        sender.getUserAccount().updateProfile("변경한 이름", true);
        Long roomId = room.getId();
        Long senderId = sender.getId();
        entityManager.flush();
        entityManager.clear();

        // 이름이 변경돼도 인용은 답장 당시의 작성자·줄바꿈을 유지.
        var reloaded = service.messages(senderId, roomId, null, null, null).messages().stream()
                .filter(item -> item.id().equals(response.id())).findFirst().orElseThrow();
        assertThat(reloaded.reply()).isEqualTo(new ChatRoomResponse.Reply(original.getId(), "원문 작성자", "첫째 줄\n둘째 줄"));
        assertThat(reloaded.content()).isEqualTo("답장 본문");
    }

    @Test
    void replyToAnotherRoomIsRejectedEvenWhenBothRoomsAreAccessible() {
        ChatRoom otherRoom = rooms.save(new ChatRoom(sender.getCompany(), ChatRoomType.SELF, null, sender));
        participants.save(new ChatRoomMember(otherRoom, sender));
        ChatMessage original = messages.save(new ChatMessage(otherRoom, sender, "다른 방 원문", ChatMessageType.TEXT));
        long count = messages.count();

        assertThatThrownBy(() -> service.sendMessage(sender.getId(), room.getId(),
                new ChatRoomRequest.MessageCreateRequest("잘못된 답장", List.of(), original.getId())))
                .isInstanceOf(ChatException.class).hasMessage("원문 메시지를 찾을 수 없습니다.");
        assertThat(messages.count()).isEqualTo(count);
    }

    @Test
    void systemNoticeCannotBeReplyTarget() {
        ChatMessage original = messages.save(new ChatMessage(room, sender, "입장 안내", ChatMessageType.SYSTEM));
        assertThatThrownBy(() -> service.sendMessage(sender.getId(), room.getId(),
                new ChatRoomRequest.MessageCreateRequest("답장", List.of(), original.getId())))
                .isInstanceOf(ChatException.class).hasMessage("시스템 안내에는 답장할 수 없습니다.");
    }

    @Test
    void nonParticipantCannotNavigateToOriginal() {
        UserAccount account = accounts.save(new UserAccount("outsider@example.com", "test-hash", "외부 사용자"));
        Membership outsider = memberships.save(new Membership(sender.getCompany(), account, null, Role.USER));
        ChatMessage original = messages.save(new ChatMessage(room, sender, "접근 제한 원문", ChatMessageType.TEXT));
        assertThatThrownBy(() -> service.messages(outsider.getId(), room.getId(), null, original.getId(), null))
                .isInstanceOf(ChatException.class).hasMessage("채팅방 참가자가 아닙니다.");
    }

    @Test
    void anchorAndNextPagesKeepMessageOrderWithoutGaps() {
        List<ChatMessage> history = IntStream.range(0, 125)
                .mapToObj(index -> messages.save(new ChatMessage(room, sender, "대화 " + index, ChatMessageType.TEXT)))
                .toList();
        var anchor = service.messages(sender.getId(), room.getId(), null, history.get(59).getId(), null);
        var next = service.messages(sender.getId(), room.getId(), null, null, anchor.newerCursor());
        var last = service.messages(sender.getId(), room.getId(), null, null, next.newerCursor());
        var previous = service.messages(sender.getId(), room.getId(), anchor.nextCursor(), null, null);

        assertThat(anchor.messages()).extracting(ChatRoomResponse.Message::id)
                .containsExactlyElementsOf(history.subList(10, 60).stream().map(ChatMessage::getId).toList());
        assertThat(next.messages()).extracting(ChatRoomResponse.Message::id)
                .containsExactlyElementsOf(history.subList(60, 110).stream().map(ChatMessage::getId).toList());
        assertThat(last.messages()).extracting(ChatRoomResponse.Message::id)
                .containsExactlyElementsOf(history.subList(110, 125).stream().map(ChatMessage::getId).toList());
        assertThat(previous.messages()).hasSize(10);
        assertThat(last.newerCursor()).isNull();
        assertThat(previous.nextCursor()).isNull();
    }

    @Test
    void cursorModesCannotBeCombined() {
        assertThatThrownBy(() -> service.messages(sender.getId(), room.getId(), 1L, 2L, null))
                .isInstanceOf(ChatException.class).hasMessage("메시지 조회 위치가 올바르지 않습니다.");
    }

    @Test
    void normalMessageDoesNotRequireReply() {
        var response = service.sendMessage(sender.getId(), room.getId(),
                new ChatRoomRequest.MessageCreateRequest("일반 메시지", null, null));
        assertThat(response.reply()).isNull();
        assertThat(response.content()).isEqualTo("일반 메시지");
    }

    @Test
    void replyMigrationPreservesExistingRowsAndEnforcesOriginalReference() throws SQLException {
        // 새 마이그레이션만 별도 DB에 적용해 기존 행과 원문 FK를 검증.
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:replyMigration;MODE=PostgreSQL", "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE chat_message (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO chat_message (id) VALUES (1)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V18__add_chat_message_reply.sql"));
            statement.execute("INSERT INTO chat_message (id, reply_to_message_id, reply_to_sender_name, reply_to_content) "
                    + "VALUES (2, 1, '작성자', '원문')");
            try (var result = statement.executeQuery("SELECT reply_to_message_id FROM chat_message WHERE id = 1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1)).isNull();
            }
            assertThatThrownBy(() -> statement.execute("INSERT INTO chat_message (id, reply_to_message_id) VALUES (3, 999)"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
