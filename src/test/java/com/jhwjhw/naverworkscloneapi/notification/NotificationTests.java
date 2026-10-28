package com.jhwjhw.naverworkscloneapi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhwjhw.naverworkscloneapi.chat.ChatRoomRequest;
import com.jhwjhw.naverworkscloneapi.chat.ChatRoomService;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatNotificationMode;
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
import com.jhwjhw.naverworkscloneapi.notification.domain.NotificationCategory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
        "spring.datasource.url=jdbc:h2:mem:notificationTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
// 메시지 전송이 받는 사람별 알림 설정을 지켜 서비스 알림을 남기고, API가 본인 알림만 다루는지 검증.
class NotificationTests {
    @Autowired private ChatRoomService chatRoomService;
    @Autowired private NotificationService service;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository accounts;
    @Autowired private MembershipRepository memberships;
    @Autowired private ChatRoomRepository rooms;
    @Autowired private ChatRoomMemberRepository participants;
    @Autowired private ApplicationEvents events;
    @Autowired private MockMvc mvc;
    @Autowired private JwtEncoder encoder;

    private Membership author;
    private Membership listener;
    private Membership muted;
    private Membership mentionOnly;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        Company company = companies.save(new Company("알림 테스트 " + UUID.randomUUID()));
        author = createMember(company);
        listener = createMember(company);
        muted = createMember(company);
        mentionOnly = createMember(company);
        room = rooms.save(new ChatRoom(company, ChatRoomType.GROUP, "알림방", author));
        participants.save(new ChatRoomMember(room, author));
        participants.save(new ChatRoomMember(room, listener));
        ChatRoomMember mutedMember = participants.save(new ChatRoomMember(room, muted));
        mutedMember.updateNotificationMode(ChatNotificationMode.NONE);
        ChatRoomMember mentionMember = participants.save(new ChatRoomMember(room, mentionOnly));
        mentionMember.updateNotificationMode(ChatNotificationMode.MENTION);
    }

    private Membership createMember(Company company) {
        UserAccount account = accounts.save(new UserAccount(UUID.randomUUID() + "@example.com", "test-hash", "테스트 사용자"));
        return memberships.save(new Membership(company, account, null, Role.USER));
    }

    private void send(String content, List<Long> mentions) {
        chatRoomService.sendMessage(author.getId(), room.getId(),
                new ChatRoomRequest.MessageCreateRequest(content, mentions, null));
    }

    private String token(Membership member) {
        Instant now = Instant.now();
        return encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                .issuer("naverworks-clone-api-test").subject("membership:" + member.getId())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("membershipId", member.getId())
                .claim("role", member.getRole().name()).build())).getTokenValue();
    }

    @Test
    void messageNotifiesOnlyRecipientsWhoseRoomSettingAllowsIt() {
        send("일반 메시지", List.of());
        assertThat(service.list(author.getId(), null)).isEmpty();
        assertThat(service.list(muted.getId(), null)).isEmpty();
        assertThat(service.list(mentionOnly.getId(), null)).isEmpty();
        List<NotificationResponse.Item> received = service.list(listener.getId(), null);
        assertThat(received).hasSize(1);
        assertThat(received.getFirst().category()).isEqualTo("MESSAGE");
        assertThat(received.getFirst().title()).isEqualTo("알림방");
        assertThat(received.getFirst().body()).isEqualTo("테스트 사용자: 일반 메시지");
        assertThat(received.getFirst().linkPath()).isEqualTo("/message?roomId=" + room.getId());
        assertThat(received.getFirst().read()).isFalse();
        // 받는 사람 수만큼 커밋 이후 전달용 이벤트가 발행됨.
        assertThat(events.stream(NotificationCreatedEvent.class).map(NotificationCreatedEvent::membershipId))
                .containsExactly(listener.getId());
    }

    @Test
    void mentionOnlyMemberIsNotifiedWhenMentioned() {
        send("@멘션 메시지", List.of(mentionOnly.getId()));
        assertThat(service.list(mentionOnly.getId(), NotificationCategory.MESSAGE)).hasSize(1);
        assertThat(service.list(mentionOnly.getId(), NotificationCategory.SYSTEM)).isEmpty();
    }

    @Test
    void apiListsOwnNotificationsAndMarksThemRead() throws Exception {
        send("첫 번째", List.of());
        send("두 번째", List.of());
        mvc.perform(get("/api/v1/notification").header("Authorization", "Bearer " + token(listener)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                // 최신 알림이 먼저 옴.
                .andExpect(jsonPath("$.data[0].body").value("테스트 사용자: 두 번째"))
                .andExpect(jsonPath("$.data[0].read").value(false));
        mvc.perform(post("/api/v1/notification/readAll").header("Authorization", "Bearer " + token(listener)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(2));
        assertThat(service.list(listener.getId(), null)).allMatch(NotificationResponse.Item::read);
    }

    @Test
    void apiRejectsReadingAnotherMembersNotification() throws Exception {
        send("남의 알림", List.of());
        Long notificationId = service.list(listener.getId(), null).getFirst().id();
        mvc.perform(post("/api/v1/notification/read").param("notificationId", notificationId.toString())
                .header("Authorization", "Bearer " + token(author)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        assertThat(service.list(listener.getId(), null).getFirst().read()).isFalse();
    }
}
