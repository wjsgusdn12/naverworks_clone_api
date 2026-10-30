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
    private static final ApiMetricBucketRepository.Ring FIVE = ApiMetricBucketRepository.Ring.FIVE_MINUTES;
    @Autowired private MockMvc mvc;
    @Autowired private JwtEncoder encoder;
    @Autowired private ApiDashboardService dashboardService;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository accounts;
    @Autowired private MembershipRepository memberships;
    @Autowired private ApiMetricHourlyRepository hourlyRepository;
    @Autowired private ApiMetricBucketRepository bucketRepository;

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
        long before = dashboardService.dashboard(ApiDashboardService.Range.HOUR).summary().requests();
        mvc.perform(get("/api/v1/chatRoom").header("Authorization", bearer(user))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/chatRoom").header("Authorization", bearer(user))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/chatRoom")).andExpect(status().isUnauthorized());
        mvc.perform(get(ApiDashboardController.PATH).header("Authorization", bearer(admin)));

        // 최근 60분: 지금 분까지 1분 칸 60개(정각과 무관하게 밀려감), 점들을 더하면 숫자 카드 합계.
        ApiDashboardResponse hour = dashboardService.dashboard(ApiDashboardService.Range.HOUR);
        assertThat(hour.range()).isEqualTo("HOUR");
        assertThat(hour.mode()).isEqualTo("LOCAL");
        assertThat(hour.database()).isEqualTo("UP");
        assertThat(hour.summary().requests()).isEqualTo(before + 3);
        assertThat(hour.summary().clientErrors()).isGreaterThanOrEqualTo(1);
        java.time.ZoneId seoul = java.time.ZoneId.of(hour.zone());
        assertThat(hour.trend()).hasSize(ApiDashboardService.HOUR_MINUTES);
        assertThat(java.time.Duration.between(hour.trend().getFirst().startAt(), hour.trend().getLast().startAt()))
                .isEqualTo(java.time.Duration.ofMinutes(59));
        assertThat(hour.trend().getLast().startAt()).isBeforeOrEqualTo(hour.generatedAt());
        assertThat(java.time.Duration.between(hour.trend().getLast().startAt(), hour.generatedAt()))
                .isLessThan(java.time.Duration.ofMinutes(1));
        assertThat(hour.trend().stream().mapToLong(ApiDashboardResponse.TrendPoint::requests).sum())
                .isEqualTo(hour.summary().requests());
        // 히트맵용 응답 시간 구간별 개수(9칸)를 더하면 그 칸의 요청 수.
        assertThat(hour.trend()).allSatisfy(point -> {
            assertThat(point.latency()).hasSize(MetricsBucket.LATENCY_BOUNDS_MS.length + 1);
            assertThat(point.latency().stream().mapToLong(Long::longValue).sum()).isEqualTo(point.requests());
        });
        assertThat(hour.ranking().top()).anyMatch(stat -> stat.endpoint().equals("GET /api/v1/chatRoom"));
        assertThat(hour.ranking().bins()).hasSize(ApiDashboardService.HOUR_MINUTES);
        assertThat(hour.servers()).anyMatch(ApiDashboardResponse.Server::current);

        // 오늘: 0시부터 5분 칸 288개, 대시보드 자신의 조회는 순위에서 빠짐.
        ApiDashboardResponse day = dashboardService.dashboard(ApiDashboardService.Range.DAY);
        assertThat(day.trend()).hasSize(ApiDashboardService.DAY_BUCKETS);
        assertThat(day.trend().getFirst().startAt().atZone(seoul).getHour()).isZero();
        java.time.ZonedDateTime dayLast = day.trend().getLast().startAt().atZone(seoul);
        assertThat(dayLast.getHour()).isEqualTo(23);
        assertThat(dayLast.getMinute()).isEqualTo(55);
        assertThat(day.trend().stream().mapToLong(ApiDashboardResponse.TrendPoint::requests).sum())
                .isEqualTo(day.summary().requests());
        assertThat(day.ranking().top()).anySatisfy(stat -> {
            assertThat(stat.endpoint()).isEqualTo("GET /api/v1/chatRoom");
            assertThat(stat.requests()).isGreaterThanOrEqualTo(2);
        });
        assertThat(day.ranking().top()).noneMatch(stat -> stat.endpoint().contains("apiDashboard"));
        assertThat(day.ranking().bins()).hasSize(ApiDashboardService.DAY_BUCKETS);
        assertThat(day.ranking().top().getFirst().trendRequests()).hasSize(ApiDashboardService.DAY_BUCKETS);

        // 이번 주: 일요일 0시~토요일 23시 1시간 칸 168개, 순위 추이선은 하루 7칸.
        ApiDashboardResponse week = dashboardService.dashboard(ApiDashboardService.Range.WEEK);
        assertThat(week.trend()).hasSize(ApiDashboardService.WEEK_HOURS);
        java.time.ZonedDateTime weekFirst = week.trend().getFirst().startAt().atZone(seoul);
        assertThat(weekFirst.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);
        assertThat(weekFirst.getHour()).isZero();
        java.time.ZonedDateTime weekLast = week.trend().getLast().startAt().atZone(seoul);
        assertThat(weekLast.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SATURDAY);
        assertThat(weekLast.getHour()).isEqualTo(23);
        assertThat(week.summary().requests()).isGreaterThanOrEqualTo(hour.summary().requests());
        assertThat(week.ranking().bins()).hasSize(7);
        assertThat(week.ranking().top()).anySatisfy(stat -> {
            assertThat(stat.endpoint()).isEqualTo("GET /api/v1/chatRoom");
            assertThat(stat.trendRequests()).hasSize(7);
            assertThat(stat.trendRequests().stream().mapToLong(Long::longValue).sum()).isEqualTo(stat.requests());
        });

        // 이번 달: 1일 0시~말일 23시 1시간 칸, 점들을 더하면 이번 달 합계.
        ApiDashboardResponse month = dashboardService.dashboard(ApiDashboardService.Range.MONTH);
        java.time.ZonedDateTime monthFirst = month.trend().getFirst().startAt().atZone(seoul);
        assertThat(monthFirst.getDayOfMonth()).isEqualTo(1);
        assertThat(monthFirst.getHour()).isZero();
        assertThat(month.trend()).hasSize(monthFirst.toLocalDate().lengthOfMonth() * 24);
        assertThat(month.trend().getLast().startAt().atZone(seoul).getHour()).isEqualTo(23);
        assertThat(month.trend().stream().mapToLong(ApiDashboardResponse.TrendPoint::requests).sum())
                .isEqualTo(month.summary().requests());
        assertThat(month.ranking().bins()).hasSize(month.trend().size());
        assertThat(month.ranking().top()).anySatisfy(stat -> {
            assertThat(stat.trendRequests()).hasSize(month.trend().size());
            assertThat(stat.trendRequests().stream().mapToLong(Long::longValue).sum()).isEqualTo(stat.requests());
        });
    }

    // 핵심: 서버 1·2가 각자 모은 기록을 같은 DB에 보내면 어느 쪽에서 읽어도 합계가 나오고, 재시작해도 남음.
    // (실제 요청이 쓰는 지금 칸과 겹치지 않도록 먼 미래 시각을 사용)
    @Test
    void twoServersWritingToOneDatabaseAreSummed() {
        ApiMetricsRecorder server1 = new ApiMetricsRecorder(bucketRepository, mock(ApiMetricHourlyRepository.class));
        ApiMetricsRecorder server2 = new ApiMetricsRecorder(bucketRepository, mock(ApiMetricHourlyRepository.class));
        Instant at = Instant.parse("2099-01-01T10:02:00Z");
        for (int i = 0; i < 3; i++) server1.record("GET /api/v1/chatRoom", 200, 40, at);
        server2.record("GET /api/v1/chatRoom", 200, 60, at);
        server2.record("POST /api/v1/chatRoom/message/create", 500, 120, at);
        server1.flush();
        // 서버 2는 꺼지면서 남은 기록을 저장(재시작 직전 기록도 사라지지 않음).
        server2.flushOnShutdown();

        long bucket = ApiMetricsRecorder.bucketOf(at);
        MetricsBucket total = bucketRepository.read(FIVE, bucket, bucket).get(bucket);
        assertThat(total.getCount()).isEqualTo(5);
        assertThat(total.getServerErrors()).isEqualTo(1);
        assertThat(total.p95Ms()).isEqualTo(200);
        assertThat(total.getEndpoints().get("GET /api/v1/chatRoom").getCount()).isEqualTo(4);
        assertThat(total.getEndpoints().get("GET /api/v1/chatRoom").getDurationSumMs()).isEqualTo(3 * 40 + 60);
        // 보낸 뒤에는 각 서버의 상자가 비어 같은 기록이 두 번 더해지지 않음.
        server1.flush();
        assertThat(bucketRepository.read(FIVE, bucket, bucket).get(bucket).getCount()).isEqualTo(5);
        // 같은 기록이 1분 칸에도 들어가 이번 시간 1분 그래프에 쓰임.
        long minute = ApiMetricsRecorder.minuteOf(at);
        assertThat(bucketRepository.read(ApiMetricBucketRepository.Ring.ONE_MINUTE, minute, minute).get(minute)
                .getCount()).isEqualTo(5);
    }

    // 5분 칸 자리는 300개를 돌려 씀: 25시간 뒤 같은 자리에 오면 옛 칸을 지우지 않고 새 값으로 덮어씀.
    @Test
    void bucketSlotIsReusedAfter25Hours() {
        long old = ApiMetricsRecorder.bucketOf(Instant.parse("2099-02-01T00:00:00Z"));
        long reused = old + FIVE.slots();
        MetricsBucket oldData = new MetricsBucket();
        oldData.record("GET /old", 200, 40);
        oldData.record("GET /old", 200, 40);
        bucketRepository.addBucket(FIVE, old, oldData);
        MetricsBucket newData = new MetricsBucket();
        newData.record("GET /new", 500, 3000);
        bucketRepository.addBucket(FIVE, reused, newData);

        // 새 칸은 옛 값이 섞이지 않고(더하기가 아니라 덮어쓰기) 새 기록만.
        MetricsBucket saved = bucketRepository.read(FIVE, reused, reused).get(reused);
        assertThat(saved.getCount()).isEqualTo(1);
        assertThat(saved.getServerErrors()).isEqualTo(1);
        assertThat(saved.getEndpoints()).containsOnlyKeys("GET /new");
        // 옛 칸의 합계 행은 새 칸으로 바뀌어 옛 칸 합계는 0(그 뒤 안 불린 API 행은 남지만 25시간 지난 범위라
        // 대시보드가 읽지 않고, 행 수도 300자리 × API 수를 넘지 않음).
        assertThat(bucketRepository.read(FIVE, old, old).getOrDefault(old, new MetricsBucket()).getCount()).isZero();
        // 늦게 도착한 옛 칸 기록이 새 칸을 덮어쓰지 않음.
        bucketRepository.addBucket(FIVE, old, oldData);
        assertThat(bucketRepository.read(FIVE, reused, reused).get(reused).getCount()).isEqualTo(1);
    }

    // 5분 칸이 생기기 전(또는 비어 있는) 시간도 1시간 요약(DB)으로 채워 오늘 숫자·그래프·순위에서 빠지지 않음.
    @Test
    void todayFillsHoursWithoutFineRecordsFromHourlySummary() {
        java.time.ZoneId seoul = java.time.ZoneId.of("Asia/Seoul");
        java.time.ZonedDateTime local = Instant.now().atZone(seoul);
        // 지금 시각과 겹치지 않는 오늘의 한 시간(실제 요청은 지금 시간에만 쌓임)
        int hourIndex = (local.getHour() + 12) % 24;
        Instant hour = local.toLocalDate().atStartOfDay(seoul).toInstant().plusSeconds(hourIndex * 3600L);
        MetricsBucket past = new MetricsBucket();
        for (int i = 0; i < 7; i++) past.record("GET /api/v1/pastOnly", 200, 40);
        hourlyRepository.addHour(hour, past);

        ApiDashboardResponse dashboard = dashboardService.dashboard(ApiDashboardService.Range.DAY);
        // 그 시간의 5분 칸 12개에 1시간 합계를 고르게 나눔(7건 → 앞 7칸에 1건씩), 모두 추정값 표시.
        int first = hourIndex * 12;
        java.util.List<ApiDashboardResponse.TrendPoint> cells = dashboard.trend().subList(first, first + 12);
        assertThat(cells.getFirst().startAt()).isEqualTo(hour);
        assertThat(cells).allSatisfy(item -> {
            assertThat(item.estimated()).isTrue();
            assertThat(item.spanMinutes()).isEqualTo(5);
        });
        assertThat(cells).extracting(ApiDashboardResponse.TrendPoint::requests)
                .containsExactly(1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L, 0L, 0L, 0L, 0L);
        // 나눠도 평균 응답 시간은 그대로(40ms).
        assertThat(cells.getFirst().avgMs()).isEqualTo(40);
        // 숫자 카드·그래프 합계가 1시간 요약과 맞고, 순위에도 그 시간의 API가 들어감.
        assertThat(dashboard.summary().requests()).isGreaterThanOrEqualTo(7);
        assertThat(dashboard.trend().stream().mapToLong(ApiDashboardResponse.TrendPoint::requests).sum())
                .isEqualTo(dashboard.summary().requests());
        assertThat(dashboard.ranking().top()).anySatisfy(stat -> {
            assertThat(stat.endpoint()).isEqualTo("GET /api/v1/pastOnly");
            assertThat(stat.requests()).isEqualTo(7);
            assertThat(stat.trendRequests().subList(first, first + 12).stream().mapToLong(Long::longValue).sum())
                    .isEqualTo(7);
        });
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
        // 95번째 요청은 2000~5000ms 구간 10건 중 5번째 → 구간 안에서 비례 계산하면 3500ms
        assertThat(slow.p95Ms()).isEqualTo(3500);

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
