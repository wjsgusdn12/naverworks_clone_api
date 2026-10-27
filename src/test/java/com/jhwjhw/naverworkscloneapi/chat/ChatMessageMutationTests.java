package com.jhwjhw.naverworkscloneapi.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:chatMutationTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
// 운영 DB와 분리해 실제 저장·인가·동시 변경·커밋 이후 전파를 검증.
class ChatMessageMutationTests {
    @Autowired private ChatRoomService service;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository accounts;
    @Autowired private MembershipRepository memberships;
    @Autowired private ChatRoomRepository rooms;
    @Autowired private ChatRoomMemberRepository participants;
    @Autowired private ChatMessageRepository messages;
    @Autowired private EntityManager entityManager;
    @Autowired private ApplicationEvents events;
    @Autowired private MockMvc mvc;
    @Autowired private JwtEncoder encoder;
    @MockitoSpyBean private ChatRealtimeEventPublisher realtimePublisher;

    private Membership author;
    private Membership other;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        Company company = companies.save(new Company("메시지 변경 테스트 " + UUID.randomUUID()));
        author = createMember(company, Role.USER);
        other = createMember(company, Role.ADMIN);
        room = rooms.save(new ChatRoom(company, ChatRoomType.DIRECT, null, null));
        participants.save(new ChatRoomMember(room, author));
        participants.save(new ChatRoomMember(room, other));
    }

    private Membership createMember(Company company, Role role) {
        var account = accounts.save(new UserAccount(UUID.randomUUID() + "@example.com", "test-hash", "테스트 사용자"));
        return memberships.save(new Membership(company, account, null, role));
    }

    private ChatRoomResponse.Message send(String content) {
        return service.sendMessage(author.getId(), room.getId(), new ChatRoomRequest.MessageCreateRequest(content, List.of(), null));
    }

    private ChatRoomRequest.MessageUpdateRequest edit(String content, long version) {
        return new ChatRoomRequest.MessageUpdateRequest(content, List.of(), version);
    }

    private String token(Membership member) {
        Instant now = Instant.now();
        return encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                .issuer("naverworks-clone-api-test").subject("membership:" + member.getId())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("membershipId", member.getId())
                .claim("role", member.getRole().name()).build())).getTokenValue();
    }

    @Test
    void editPreservesMessageIdentityAndPersistsNewContent() {
        var original = send("기존 본문");
        var updated = service.updateMessage(author.getId(), room.getId(), original.id(), edit("수정한 본문\n두 번째 줄", original.version()));
        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.createdAt()).isEqualTo(original.createdAt());
        assertThat(updated.content()).isEqualTo("수정한 본문\n두 번째 줄");
        assertThat(updated.editedAt()).isNotNull();
        assertThat(updated.version()).isGreaterThan(original.version());
        entityManager.clear();
        assertThat(messages.findById(original.id()).orElseThrow().getContent()).isEqualTo(updated.content());
        assertThat(events.stream(ChatMessageChangedEvent.class).map(ChatMessageChangedEvent::type)).contains("MESSAGE_UPDATED");
    }

    @Test
    void administratorCannotEditOrDeleteAnotherAuthorsMessageThroughApi() throws Exception {
        var original = send("작성자 전용");
        mvc.perform(post("/api/v1/chatRoom/message/update/" + original.id()).param("chatRoomId", room.getId().toString())
                .header("Authorization", "Bearer " + token(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"변조\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHAT_MESSAGE_AUTHOR_REQUIRED"));
        mvc.perform(post("/api/v1/chatRoom/message/delete/" + original.id()).param("chatRoomId", room.getId().toString())
                .header("Authorization", "Bearer " + token(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
        assertThat(messages.findById(original.id()).orElseThrow().getContent()).isEqualTo("작성자 전용");
    }

    @Test
    void apiRejectsBlankContentAndMissingVersion() throws Exception {
        var original = send("원문");
        for (String body : List.of("{\"content\":\"  \",\"expectedVersion\":0}", "{\"content\":\"변경\"}")) {
            mvc.perform(post("/api/v1/chatRoom/message/update/" + original.id()).param("chatRoomId", room.getId().toString())
                    .header("Authorization", "Bearer " + token(author)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void staleEditorGetsConflictInsteadOfOverwritingNewerContent() throws Exception {
        var original = send("처음");
        service.updateMessage(author.getId(), room.getId(), original.id(), edit("최신", original.version()));
        mvc.perform(post("/api/v1/chatRoom/message/update/" + original.id()).param("chatRoomId", room.getId().toString())
                .header("Authorization", "Bearer " + token(author)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"오래된 화면\",\"expectedVersion\":" + original.version() + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHAT_MESSAGE_CONFLICT"));
        assertThat(messages.findById(original.id()).orElseThrow().getContent()).isEqualTo("최신");
    }

    @Test
    void deleteHidesContentAndRemovesUnreadCountWithoutRemovingMessage() {
        var original = service.sendMessage(author.getId(), room.getId(),
                new ChatRoomRequest.MessageCreateRequest("비공개 본문", List.of(other.getId()), null));
        assertThat(service.list(other.getId()).getFirst().unreadCount()).isEqualTo(1);
        var deleted = service.deleteMessage(author.getId(), room.getId(), original.id(), original.version());
        assertThat(deleted.deletedAt()).isNotNull();
        assertThat(deleted.content()).isEqualTo(ChatMessage.DELETED_CONTENT);
        assertThat(deleted.mentionedMembershipIds()).isEmpty();
        assertThat(deleted.readCount()).isZero();
        var listed = service.list(other.getId()).getFirst();
        assertThat(listed.unreadCount()).isZero();
        assertThat(listed.lastMessage()).isEqualTo(ChatMessage.DELETED_CONTENT);
        assertThat(listed.lastMessageId()).isEqualTo(original.id());
        assertThat(listed.lastMessageAt()).isEqualTo(original.createdAt());
        assertThat(messages.existsById(original.id())).isTrue();
    }

    @Test
    void deletedOriginalIsHiddenInsideRepliesAfterReload() {
        var original = send("인용하면 안 될 삭제 원문");
        var reply = service.sendMessage(other.getId(), room.getId(),
                new ChatRoomRequest.MessageCreateRequest("답장", List.of(), original.id()));
        service.deleteMessage(author.getId(), room.getId(), original.id(), original.version());
        entityManager.clear();
        var reloaded = service.messages(other.getId(), room.getId(), null, null, null).messages().stream()
                .filter(item -> item.id().equals(reply.id())).findFirst().orElseThrow();
        assertThat(reloaded.reply().deleted()).isTrue();
        assertThat(reloaded.reply().content()).isEqualTo(ChatMessage.DELETED_CONTENT);
    }

    @Test
    void existingReplyKeepsSnapshotWhenOriginalIsEdited() {
        var original = send("인용 당시 내용");
        var reply = service.sendMessage(other.getId(), room.getId(), new ChatRoomRequest.MessageCreateRequest("답장", List.of(), original.id()));
        service.updateMessage(author.getId(), room.getId(), original.id(), edit("변경한 원문", original.version()));
        entityManager.clear();
        var reloaded = service.messages(other.getId(), room.getId(), null, null, null).messages().stream()
                .filter(item -> item.id().equals(reply.id())).findFirst().orElseThrow();
        assertThat(reloaded.reply().content()).isEqualTo("인용 당시 내용");
    }

    @Test
    void repeatedDeleteDoesNotPublishAnotherChange() {
        var original = send("삭제 대상");
        var deleted = service.deleteMessage(author.getId(), room.getId(), original.id(), original.version());
        var repeated = service.deleteMessage(author.getId(), room.getId(), original.id(), original.version());
        assertThat(repeated.version()).isEqualTo(deleted.version());
        assertThat(events.stream(ChatMessageChangedEvent.class).filter(event -> event.type().equals("MESSAGE_DELETED")).count()).isEqualTo(1);
    }

    @Test
    void deletedMessageCannotBeEditedOrRepliedTo() {
        var original = send("삭제 대상");
        var deleted = service.deleteMessage(author.getId(), room.getId(), original.id(), original.version());
        assertThatThrownBy(() -> service.updateMessage(author.getId(), room.getId(), original.id(), edit("복구 시도", deleted.version())))
                .isInstanceOf(ChatException.class).hasMessage("이미 삭제된 메시지입니다.");
        assertThatThrownBy(() -> service.sendMessage(other.getId(), room.getId(), new ChatRoomRequest.MessageCreateRequest("답장", List.of(), original.id())))
                .isInstanceOf(ChatException.class).hasMessage("이미 삭제된 메시지입니다.");
    }

    @Test
    void systemNoticeAndOtherRoomCannotBeChanged() {
        var system = messages.saveAndFlush(new ChatMessage(room, author, "시스템", ChatMessageType.SYSTEM));
        assertThatThrownBy(() -> service.deleteMessage(author.getId(), room.getId(), system.getId(), system.getVersion()))
                .isInstanceOf(ChatException.class).hasMessage("시스템 안내는 수정·삭제할 수 없습니다.");
        var original = send("다른 방 접근 차단");
        var anotherRoom = rooms.save(new ChatRoom(author.getCompany(), ChatRoomType.SELF, null, author));
        participants.save(new ChatRoomMember(anotherRoom, author));
        assertThatThrownBy(() -> service.updateMessage(author.getId(), anotherRoom.getId(), original.id(), edit("변조", original.version())))
                .isInstanceOf(ChatException.class).hasMessage("원문 메시지를 찾을 수 없습니다.");
    }

    @Test
    void participantWhoLeftCannotMutateTheirOldMessage() {
        var third = createMember(author.getCompany(), Role.USER);
        var group = service.createGroup(author.getId(), new ChatRoomRequest.GroupCreateRequest("퇴장 테스트", List.of(other.getId(), third.getId())));
        var original = service.sendMessage(author.getId(), group.id(), new ChatRoomRequest.MessageCreateRequest("퇴장 전 메시지", List.of(), null));
        service.transferOwner(author.getId(), group.id(), other.getId());
        service.leave(author.getId(), group.id());
        assertThatThrownBy(() -> service.deleteMessage(author.getId(), group.id(), original.id(), original.version()))
                .isInstanceOf(ChatException.class).hasMessage("채팅방 참가자가 아닙니다.");
    }

    @Test
    void optimisticLockRejectsConcurrentStaleEntity() {
        var original = send("처음");
        var stale = messages.findById(original.id()).orElseThrow();
        entityManager.detach(stale);
        service.updateMessage(author.getId(), room.getId(), original.id(), edit("먼저 저장", original.version()));
        stale.editContent("뒤늦은 저장", java.util.Set.of());
        assertThatThrownBy(() -> messages.saveAndFlush(stale)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void realTimeChangeIsPublishedOnlyAfterCommitAndNotAfterRollback() {
        var original = send("커밋 테스트");
        var edited = service.updateMessage(author.getId(), room.getId(), original.id(), edit("커밋 완료", original.version()));
        verify(realtimePublisher, never()).publishMessageChanged(any());
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(realtimePublisher, times(1)).publishMessageChanged(any());
        TestTransaction.start();
        service.deleteMessage(author.getId(), room.getId(), original.id(), edited.version());
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verify(realtimePublisher, times(1)).publishMessageChanged(any());
        TestTransaction.start();
        assertThat(messages.findById(original.id()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void migrationAddsVersionWithoutChangingExistingRows() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:mutationMigration;MODE=PostgreSQL", "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE chat_message (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO chat_message (id) VALUES (1)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V19__add_chat_message_edit_delete.sql"));
            try (var result = statement.executeQuery("SELECT version, edited_at, deleted_at FROM chat_message WHERE id=1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isZero();
                assertThat(result.getObject(2)).isNull();
                assertThat(result.getObject(3)).isNull();
            }
        }
    }
}
