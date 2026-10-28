package com.jhwjhw.naverworkscloneapi.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:chatReactionTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
// 리액션 추가·취소의 멱등성, 집계·사용자 목록, 허용 범위 검사, 커밋 이후 이벤트 발행을 검증.
class ChatReactionTests {
    @Autowired private ChatRoomService service;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository accounts;
    @Autowired private MembershipRepository memberships;
    @Autowired private ChatRoomRepository rooms;
    @Autowired private ChatRoomMemberRepository participants;
    @Autowired private ApplicationEvents events;
    @Autowired private MockMvc mvc;
    @Autowired private JwtEncoder encoder;

    private Membership author;
    private Membership other;
    private Membership outsider;
    private ChatRoom room;
    private Long messageId;

    @BeforeEach
    void setUp() {
        Company company = companies.save(new Company("리액션 테스트 " + UUID.randomUUID()));
        author = createMember(company, "작성자");
        other = createMember(company, "동료");
        outsider = createMember(company, "외부인");
        room = rooms.save(new ChatRoom(company, ChatRoomType.DIRECT, null, null));
        participants.save(new ChatRoomMember(room, author));
        participants.save(new ChatRoomMember(room, other));
        messageId = service.sendMessage(author.getId(), room.getId(),
                new ChatRoomRequest.MessageCreateRequest("리액션 대상", List.of(), null)).id();
    }

    private Membership createMember(Company company, String name) {
        UserAccount account = accounts.save(new UserAccount(UUID.randomUUID() + "@example.com", "test-hash", name));
        return memberships.save(new Membership(company, account, null, Role.USER));
    }

    private String token(Membership member) {
        Instant now = Instant.now();
        return encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                .issuer("naverworks-clone-api-test").subject("membership:" + member.getId())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("membershipId", member.getId())
                .claim("role", member.getRole().name()).build())).getTokenValue();
    }

    @Test
    void addIsIdempotentAndOnePersonKeepsOnlyOneEmoji() {
        service.addReaction(other.getId(), room.getId(), messageId, "thumbs_up");
        // 같은 사람이 같은 이모지를 다시 눌러도 수가 늘지 않음(재시도 안전).
        service.addReaction(other.getId(), room.getId(), messageId, "thumbs_up");
        service.addReaction(author.getId(), room.getId(), messageId, "thumbs_up");
        // 다른 이모지를 누르면 기존 것은 취소되고 새 것으로 바뀜(1인 1개).
        List<ChatRoomResponse.Reaction> fromOther = service.addReaction(other.getId(), room.getId(), messageId, "heart");
        assertThat(fromOther).extracting(ChatRoomResponse.Reaction::emoji).containsExactly("thumbs_up", "heart");
        assertThat(fromOther.get(0).count()).isEqualTo(1);
        assertThat(fromOther.get(0).reactedByMe()).isFalse();
        assertThat(fromOther.get(1).count()).isEqualTo(1);
        assertThat(fromOther.get(1).reactedByMe()).isTrue();
        // reactedByMe는 요청한 사람 기준.
        List<ChatRoomResponse.Reaction> fromAuthor = service.removeReaction(author.getId(), room.getId(), messageId, "wow");
        assertThat(fromAuthor.get(0).reactedByMe()).isTrue();
        assertThat(fromAuthor.get(1).reactedByMe()).isFalse();
        assertThat(events.stream(ChatReactionChangedEvent.class).filter(event -> event.action().equals("added"))).hasSize(3);
        assertThat(events.stream(ChatReactionChangedEvent.class).filter(event -> event.action().equals("removed"))).hasSize(1);
    }

    @Test
    void removeIsIdempotentAndClearsEmojiWhenLastReactionGone() {
        service.addReaction(other.getId(), room.getId(), messageId, "laugh");
        List<ChatRoomResponse.Reaction> afterRemove = service.removeReaction(other.getId(), room.getId(), messageId, "laugh");
        assertThat(afterRemove).isEmpty();
        // 누른 적 없는 취소는 이벤트 없이 현재 집계만 반환.
        service.removeReaction(other.getId(), room.getId(), messageId, "laugh");
        assertThat(events.stream(ChatReactionChangedEvent.class).filter(event -> event.action().equals("removed"))).hasSize(1);
    }

    @Test
    void messageListCarriesReactionsAndMemberListShowsWhoReacted() throws Exception {
        service.addReaction(other.getId(), room.getId(), messageId, "pray");
        service.addReaction(author.getId(), room.getId(), messageId, "heart");
        mvc.perform(get("/api/v1/chatRoom/message").param("chatRoomId", room.getId().toString())
                .header("Authorization", "Bearer " + token(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].reactions[0].emoji").value("pray"))
                .andExpect(jsonPath("$.data.messages[0].reactions[0].count").value(1))
                .andExpect(jsonPath("$.data.messages[0].reactions[0].reactedByMe").value(true))
                .andExpect(jsonPath("$.data.messages[0].reactions[1].emoji").value("heart"))
                .andExpect(jsonPath("$.data.messages[0].reactions[1].reactedByMe").value(false));
        // 이모지 구분 없이 메시지 전체 리액션 사용자를 누른 순서로 돌려주고, 각자 고른 이모지가 함께 옴.
        mvc.perform(get("/api/v1/chatRoom/message/reaction/member").param("chatRoomId", room.getId().toString())
                .param("messageId", messageId.toString())
                .header("Authorization", "Bearer " + token(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].emoji").value("pray"))
                .andExpect(jsonPath("$.data[0].member.displayName").value("동료"))
                .andExpect(jsonPath("$.data[1].emoji").value("heart"))
                .andExpect(jsonPath("$.data[1].member.displayName").value("작성자"));
    }

    @Test
    void rejectsUnsupportedEmojiNonMemberAndDeletedMessage() throws Exception {
        assertThatThrownBy(() -> service.addReaction(other.getId(), room.getId(), messageId, "🔥"))
                .isInstanceOf(ChatException.class).hasMessageContaining("지원하지 않는");
        mvc.perform(post("/api/v1/chatRoom/message/reaction/add").param("chatRoomId", room.getId().toString())
                .param("messageId", messageId.toString()).header("Authorization", "Bearer " + token(outsider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"thumbs_up\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHAT_ROOM_ACCESS_DENIED"));
        service.deleteMessage(author.getId(), room.getId(), messageId, 0L);
        assertThatThrownBy(() -> service.addReaction(other.getId(), room.getId(), messageId, "thumbs_up"))
                .isInstanceOf(ChatException.class);
    }
}
