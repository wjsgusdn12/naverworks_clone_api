package com.jhwjhw.naverworkscloneapi.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
    @Autowired private ApiMetricHourlyRepository hourlyRepository;

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
        long before = dashboardService.dashboard().thisHour().requests();
        mvc.perform(get("/api/v1/chatRoom").header("Authorization", bearer(user))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/chatRoom").header("Authorization", bearer(user))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/chatRoom")).andExpect(status().isUnauthorized());
        mvc.perform(get(ApiDashboardController.PATH).header("Authorization", bearer(admin)));

        ApiDashboardResponse dashboard = dashboardService.dashboard();
        assertThat(dashboard.mode()).isEqualTo("LOCAL");
        assertThat(dashboard.database()).isEqualTo("UP");
        assertThat(dashboard.thisHour().requests()).isEqualTo(before + 3);
        assertThat(dashboard.thisHour().clientErrors()).isGreaterThanOrEqualTo(1);
        assertThat(dashboard.dayRanking().top()).anySatisfy(stat -> {
            assertThat(stat.endpoint()).isEqualTo("GET /api/v1/chatRoom");
            assertThat(stat.requests()).isGreaterThanOrEqualTo(2);
        });
        assertThat(dashboard.dayRanking().top()).noneMatch(stat -> stat.endpoint().contains("apiDashboard"));
        assertThat(dashboard.dayTrend()).hasSize(24);
        // 달력 기준: 이번 시간은 정각부터, 오늘은 0시부터, 이번 주는 일요일 0시부터(한국 시간).
        java.time.ZoneId seoul = java.time.ZoneId.of(dashboard.zone());
        java.time.ZonedDateTime hourFirst = dashboard.hourTrend().getFirst().startAt().atZone(seoul);
        assertThat(hourFirst.getMinute()).isZero();
        assertThat(dashboard.hourTrend().getLast().startAt().atZone(seoul).getMinute()).isEqualTo(55);
        java.time.ZonedDateTime dayFirst = dashboard.dayTrend().getFirst().startAt().atZone(seoul);
        assertThat(dayFirst.getHour()).isZero();
        assertThat(dashboard.dayTrend().getLast().startAt().atZone(seoul).getHour()).isEqualTo(23);
        java.time.ZonedDateTime weekFirst = dashboard.weekTrend().getFirst().startAt().atZone(seoul);
        assertThat(weekFirst.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);
        assertThat(weekFirst.getHour()).isZero();
        java.time.ZonedDateTime weekLast = dashboard.weekTrend().getLast().startAt().atZone(seoul);
        assertThat(weekLast.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SATURDAY);
        assertThat(weekLast.getHour()).isEqualTo(23);
        // 이번 달: 1일부터 말일까지 하루 한 점, 이번 달 합계는 이번 주 중 이번 달에 속한 부분 이상.
        java.time.ZonedDateTime monthFirst = dashboard.monthTrend().getFirst().startAt().atZone(seoul);
        assertThat(monthFirst.getDayOfMonth()).isEqualTo(1);
        assertThat(monthFirst.getHour()).isZero();
        assertThat(dashboard.monthTrend()).hasSize(monthFirst.toLocalDate().lengthOfMonth());
        assertThat(dashboard.monthTrend().stream().mapToLong(ApiDashboardResponse.TrendPoint::requests).sum())
                .isEqualTo(dashboard.thisMonth().requests());
        assertThat(dashboard.hourTrend()).hasSize(12);
        // 추이의 마지막 점(지금 시간대)에 방금 요청이 들어가 있고, 엔드포인트별 작은 추이선도 24칸.
        assertThat(dashboard.hourTrend().stream().mapToLong(ApiDashboardResponse.TrendPoint::requests).sum())
                .isEqualTo(dashboard.thisHour().requests());
        // 기간별 순위: 이번 시간·오늘은 방금 요청이 들어 있고, 작은 추이선 칸 수는 기간에 맞음.
        assertThat(dashboard.hourRanking().top()).anyMatch(stat -> stat.endpoint().equals("GET /api/v1/chatRoom"));
        assertThat(dashboard.hourRanking().bins()).hasSize(12);
        assertThat(dashboard.dayRanking().bins()).hasSize(24);
        assertThat(dashboard.dayRanking().top().getFirst().trendRequests()).hasSize(24);
        assertThat(dashboard.weekRanking().bins()).hasSize(7);
        assertThat(dashboard.monthRanking().bins()).hasSize(dashboard.monthTrend().size());
        // 주·월 순위는 DB의 API별 1시간 요약에서 옴(대시보드를 열 때 1시간 요약을 먼저 저장).
        assertThat(dashboard.weekRanking().top()).anySatisfy(stat -> {
            assertThat(stat.endpoint()).isEqualTo("GET /api/v1/chatRoom");
            assertThat(stat.trendRequests()).hasSize(7);
            assertThat(stat.trendRequests().stream().mapToLong(Long::longValue).sum()).isEqualTo(stat.requests());
        });
        // 일주일 그래프: DB 1시간 요약 168칸, 방금 요청도 1시간 요약에 들어감.
        assertThat(dashboard.weekTrend()).hasSize(ApiDashboardService.WEEK_HOURS);
        assertThat(dashboard.thisWeek().requests()).isGreaterThanOrEqualTo(dashboard.thisHour().requests());
        assertThat(dashboard.servers()).anyMatch(ApiDashboardResponse.Server::current);
    }

    // 핵심: 서버 1·2가 각자 모은 기록을 같은 집계판에 보내면 어느 쪽에서 읽어도 합계가 나옴.
    @Test
    void twoServersWritingToOneStoreAreSummed() {
        InMemoryApiMetricsStore sharedStore = new InMemoryApiMetricsStore();
        ApiMetricsRecorder server1 = new ApiMetricsRecorder(sharedStore, mock(ApiMetricHourlyRepository.class));
        ApiMetricsRecorder server2 = new ApiMetricsRecorder(sharedStore, mock(ApiMetricHourlyRepository.class));
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

    // 서버 1·2가 같은 시간 요약 행에 각각 더해도(첫 행 생성 + 이후 더하기) DB에 합계가 남음.
    @Test
    void hourlySummaryIsAddedNotOverwritten() {
        Instant hour = Instant.parse("2020-01-01T10:00:00Z");
        MetricsBucket fromServer1 = new MetricsBucket();
        fromServer1.record("GET /a", 200, 40);
        fromServer1.record("GET /a", 500, 3000);
        MetricsBucket fromServer2 = new MetricsBucket();
        fromServer2.record("GET /b", 404, 80);
        hourlyRepository.addHour(hour, fromServer1);
        hourlyRepository.addHour(hour, fromServer2);

        MetricsBucket saved = hourlyRepository.readTotals(hour).get(hour);
        assertThat(saved.getCount()).isEqualTo(3);
        assertThat(saved.getServerErrors()).isEqualTo(1);
        assertThat(saved.getClientErrors()).isEqualTo(1);
        assertThat(saved.getDurationSumMs()).isEqualTo(40 + 3000 + 80);
        assertThat(saved.p95Ms()).isEqualTo(5000);
        assertThat(ApiMetricsRecorder.hourOf(Instant.parse("2020-01-01T10:42:13Z"))).isEqualTo(hour);
        // API별 행도 더해져 4xx·5xx가 나뉘어 남음.
        java.util.Map<String, MetricsBucket.EndpointStats> endpoints = hourlyRepository.readEndpointTotals(hour,
                hour.plusSeconds(3600));
        assertThat(endpoints.get("GET /a").getCount()).isEqualTo(2);
        assertThat(endpoints.get("GET /a").getServerErrors()).isEqualTo(1);
        assertThat(endpoints.get("GET /b").getClientErrors()).isEqualTo(1);
        assertThat(endpoints).doesNotContainKey(ApiMetricHourlyRepository.TOTAL);
        assertThat(hourlyRepository.readEndpointHours(hour, hour.plusSeconds(3600), java.util.List.of("GET /a")))
                .singleElement().satisfies(row -> assertThat(row.durationSumMs()).isEqualTo(3040));
    }
}
