package com.jhwjhw.naverworkscloneapi.addressbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import com.jhwjhw.naverworkscloneapi.identity.domain.MemberStatusType;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.domain.Role;
import com.jhwjhw.naverworkscloneapi.identity.domain.UserAccount;
import com.jhwjhw.naverworkscloneapi.identity.repository.CompanyRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:addressBookStatusTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
// 상태 설정(바쁨·휴가 등)을 바꾸고 조회할 때 자동 해제 시각 검증과 다른 구성원 조회 반영을 확인.
class AddressBookStatusTests {
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository accounts;
    @Autowired private MembershipRepository memberships;
    @Autowired private MockMvc mvc;
    @Autowired private JwtEncoder encoder;

    private Membership me;
    private Membership colleague;

    @BeforeEach
    void setUp() {
        Company company = companies.save(new Company("상태 테스트 " + UUID.randomUUID()));
        me = createMember(company);
        colleague = createMember(company);
    }

    private Membership createMember(Company company) {
        UserAccount account = accounts.save(new UserAccount(UUID.randomUUID() + "@example.com", "test-hash", "테스트 사용자"));
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
    void setsStatusWithUntilAndClearsIt() throws Exception {
        Instant until = Instant.now().plus(Duration.ofHours(4));
        mvc.perform(post("/api/v1/addressBook/status/update").header("Authorization", "Bearer " + token(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statusType\":\"VACATION\",\"statusUntil\":\"" + until + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusType").value("VACATION"))
                .andExpect(jsonPath("$.data.presenceStatus").value("OFFLINE"));
        mvc.perform(get("/api/v1/addressBook/status/me").header("Authorization", "Bearer " + token(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusType").value("VACATION"))
                .andExpect(jsonPath("$.data.statusUntil").value(until.toString()));
        // 동료가 보는 인원 상세에도 상태가 실림.
        mvc.perform(get("/api/v1/addressBook/member/detail").param("membershipId", me.getId().toString())
                .header("Authorization", "Bearer " + token(colleague)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusType").value("VACATION"));
        // statusType 없이 보내면 해제.
        mvc.perform(post("/api/v1/addressBook/status/update").header("Authorization", "Bearer " + token(me))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusType").isEmpty())
                .andExpect(jsonPath("$.data.statusUntil").isEmpty());
    }

    @Test
    void statusWithoutUntilStaysUntilCleared() throws Exception {
        mvc.perform(post("/api/v1/addressBook/status/update").header("Authorization", "Bearer " + token(me))
                .contentType(MediaType.APPLICATION_JSON).content("{\"statusType\":\"BUSY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusType").value("BUSY"))
                .andExpect(jsonPath("$.data.statusUntil").isEmpty());
    }

    @Test
    void rejectsPastOrTooFarUntil() throws Exception {
        Instant past = Instant.now().minus(Duration.ofMinutes(1));
        mvc.perform(post("/api/v1/addressBook/status/update").header("Authorization", "Bearer " + token(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statusType\":\"AWAY\",\"statusUntil\":\"" + past + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADDRESS_BOOK_STATUS_UNTIL_INVALID"));
        Instant tooFar = Instant.now().plus(Duration.ofDays(91));
        mvc.perform(post("/api/v1/addressBook/status/update").header("Authorization", "Bearer " + token(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statusType\":\"AWAY\",\"statusUntil\":\"" + tooFar + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADDRESS_BOOK_STATUS_UNTIL_INVALID"));
    }

    @Test
    void expiredStatusReadsAsCleared() {
        // 자동 해제 시각이 지난 상태는 조회 시 설정 안 함으로 취급.
        me.updateStatus(MemberStatusType.OFF_WORK, Instant.now().minus(Duration.ofSeconds(1)));
        assertThat(me.currentStatusType()).isNull();
        assertThat(me.currentStatusUntil()).isNull();
        me.updateStatus(MemberStatusType.OFF_WORK, Instant.now().plus(Duration.ofHours(1)));
        assertThat(me.currentStatusType()).isEqualTo(MemberStatusType.OFF_WORK);
    }
}
