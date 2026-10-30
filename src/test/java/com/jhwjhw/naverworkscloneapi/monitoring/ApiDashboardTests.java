package com.jhwjhw.naverworkscloneapi.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.domain.Role;
import com.jhwjhw.naverworkscloneapi.identity.domain.UserAccount;
import com.jhwjhw.naverworkscloneapi.identity.repository.CompanyRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.UserAccountRepository;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:apiDashboardTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
// API 대시보드: 관리자 전용 권한, 요청 집계, 서버 두 대 기록의 합산, p95 계산 검증.
class ApiDashboardTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtEncoder encoder;
    @Autowired private ApiDashboardService dashboardService;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository accounts;
    @Autowired private MembershipRepository memberships;

    private Membership admin;
    private Membership user;

    @BeforeEach
    void setUp() {
        Company company = companies.save(new Company("대시보드 테스트 " + UUID.randomUUID()));
        admin = memberships.save(new Membership(company,
                accounts.save(new UserAccount(UUID.randomUUID() + "@example.com", "test-hash", "관리자")), null, Role.ADMIN));
        user = memberships.save(new Membership(company,
                accounts.save(new UserAccount(UUID.randomUUID() + "@example.com", "test-hash", "사용자")), null, Role.USER));
    }

    private String bearer(Membership member) {
        Instant now = Instant.now();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                .issuer("naverworks-clone-api-test").subject("membership:" + member.getId())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("membershipId", member.getId())
                .claim("role", member.getRole().name()).build())).getTokenValue();
    }

    @Test
    void onlyAdminCanOpenDashboard() throws Exception {
        mvc.perform(get(ApiDashboardController.PATH).header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get(ApiDashboardController.PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(ApiDashboardController.PATH).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    // 실제 요청이 엔드포인트 패턴별로 세지고, 보안에서 막힌 요청(401)도 함께 세짐. 대시보드 자신의 조회는 빠짐.
    @Test
    void realRequestsAreCountedByEndpointPattern() throws Exception {
        long before = dashboardService.dashboard().lastHour().requests();
        mvc.perform(get("/api/v1/chatRoom").header("Authorization", bearer(user))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/chatRoom").header("Authorization", bearer(user))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/chatRoom")).andExpect(status().isUnauthorized());
        mvc.perform(get(ApiDashboardController.PATH).header("Authorization", bearer(admin)));

        ApiDashboardResponse dashboard = dashboardService.dashboard();
        assertThat(dashboard.mode()).isEqualTo("LOCAL");
        assertThat(dashboard.database()).isEqualTo("UP");
        assertThat(dashboard.lastHour().requests()).isEqualTo(before + 3);
        assertThat(dashboard.lastHour().clientErrors()).isGreaterThanOrEqualTo(1);
        assertThat(dashboard.topEndpoints()).anySatisfy(stat -> {
            assertThat(stat.endpoint()).isEqualTo("GET /api/v1/chatRoom");
            assertThat(stat.requests()).isGreaterThanOrEqualTo(2);
        });
        assertThat(dashboard.topEndpoints()).noneMatch(stat -> stat.endpoint().contains("apiDashboard"));
        assertThat(dashboard.trend()).hasSize(24);
        assertThat(dashboard.servers()).anyMatch(ApiDashboardResponse.Server::current);
    }

    // 핵심: 서버 1·2가 각자 모은 기록을 같은 집계판에 보내면 어느 쪽에서 읽어도 합계가 나옴.
    @Test
    void twoServersWritingToOneStoreAreSummed() {
        InMemoryApiMetricsStore sharedStore = new InMemoryApiMetricsStore();
        ApiMetricsRecorder server1 = new ApiMetricsRecorder(sharedStore);
        ApiMetricsRecorder server2 = new ApiMetricsRecorder(sharedStore);
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) server1.record("GET /api/v1/chatRoom", 200, 40, now);
        server2.record("GET /api/v1/chatRoom", 200, 60, now);
        server2.record("POST /api/v1/chatRoom/message/create", 500, 120, now);
        server1.flush();
        server2.flush();

        long bucket = ApiMetricsRecorder.bucketOf(now);
        MetricsBucket total = sharedStore.read(bucket, bucket).get(bucket);
        assertThat(total.getCount()).isEqualTo(5);
        assertThat(total.getServerErrors()).isEqualTo(1);
        assertThat(total.getEndpoints().get("GET /api/v1/chatRoom").getCount()).isEqualTo(4);
        assertThat(total.getEndpoints().get("GET /api/v1/chatRoom").getDurationSumMs()).isEqualTo(3 * 40 + 60);
        // 보낸 뒤에는 각 서버의 상자가 비어 같은 기록이 두 번 더해지지 않음.
        server1.flush();
        assertThat(sharedStore.read(bucket, bucket).get(bucket).getCount()).isEqualTo(5);
    }

    // p95: 100건 중 95번째로 빠른 요청이 들어간 구간의 위쪽 경계.
    @Test
    void p95IsCalculatedFromLatencyHistogram() {
        MetricsBucket fast = new MetricsBucket();
        for (int i = 0; i < 95; i++) fast.record("GET /a", 200, 40);
        for (int i = 0; i < 5; i++) fast.record("GET /a", 200, 3000);
        assertThat(fast.p95Ms()).isEqualTo(50);

        MetricsBucket slow = new MetricsBucket();
        for (int i = 0; i < 90; i++) slow.record("GET /a", 200, 40);
        for (int i = 0; i < 10; i++) slow.record("GET /a", 200, 3000);
        assertThat(slow.p95Ms()).isEqualTo(5000);

        MetricsBucket verySlow = new MetricsBucket();
        verySlow.record("GET /a", 200, 9000);
        assertThat(verySlow.p95Ms()).isEqualTo(-1);
        assertThat(new MetricsBucket().p95Ms()).isZero();
    }
}
