package com.jhwjhw.naverworkscloneapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.domain.Role;
import com.jhwjhw.naverworkscloneapi.identity.domain.UserAccount;
import com.jhwjhw.naverworkscloneapi.identity.repository.CompanyRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.DepartmentRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.JobGradeRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.UserAccountRepository;
import com.jhwjhw.naverworkscloneapi.notification.repository.NotificationRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 채팅 테스트와 같은 방식: PostgreSQL 전용 마이그레이션(V14 등)은 H2에서 못 돌리므로 JPA가 스키마를 만듦.
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:authControllerTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
// 실제 HTTP 요청 흐름에 가깝게 인증 API와 Spring Security를 함께 검증.
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private JobGradeRepository jobGradeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    private Membership testMembership;

    private Membership testUserMembership;

    @BeforeEach
    void setUp() {
        // 테스트끼리 데이터가 섞이지 않도록 FK 순서의 역순으로 초기화. 로그인·가입이 남긴 알림부터 지움.
        notificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        jobGradeRepository.deleteAll();
        departmentRepository.deleteAll();
        userAccountRepository.deleteAll();
        companyRepository.deleteAll();

        // 운영 계정과 무관한 테스트 전용 관리자 계정 생성.
        Company company = new Company("테스트 회사");
        // 공개 가입 테스트가 들어갈 체험 회사로 지정.
        company.markDemo();
        company = companyRepository.save(company);
        UserAccount userAccount = userAccountRepository.save(new UserAccount(
                "admin@example.com",
                passwordEncoder.encode("safe-test-password"),
                "테스트 관리자"
        ));
        testMembership = membershipRepository.save(new Membership(company, userAccount, null, Role.ADMIN));

        UserAccount standardUser = userAccountRepository.save(new UserAccount(
                "user@example.com",
                passwordEncoder.encode("safe-user-password"),
                "테스트 일반 사용자"
        ));
        testUserMembership = membershipRepository.save(new Membership(company, standardUser, null, Role.USER));
    }

    @Test
    void loginIssuesAccessTokenAndRefreshCookieThenMeReadsMember() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"admin@example.com\",\"password\":\"safe-test-password\",\"rememberMe\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.member.role").value("ADMIN"))
                .andReturn();

        // 로그인 응답에서 액세스 토큰과 Set-Cookie 헤더를 각각 확인.
        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        JsonNode responseBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = responseBody.path("data").path("accessToken").asText();

        // 리프레시 토큰은 HttpOnly 쿠키, API 호출용 액세스 토큰은 응답 본문으로 분리.
        assertThat(setCookie).contains("refreshToken=").contains("HttpOnly").contains("Max-Age=");
        assertThat(accessToken).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_MEMBER_FOUND"))
                .andExpect(jsonPath("$.data.email").value("admin@example.com"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void loginReturnsUserRoleForStandardUser() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"user@example.com\",\"password\":\"safe-user-password\",\"rememberMe\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.role").value("USER"))
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        // 일반 사용자 토큰도 자신의 구성원 정보만 정상 조회할 수 있어야 함.
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipId").value(testUserMembership.getId()))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void loginWithRememberMeFalseIssuesSessionCookie() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        // 로그인 유지를 선택하지 않으면 브라우저 종료 시 사라지는 세션 쿠키를 발급.
                        .content("{\"loginId\":\"admin@example.com\",\"password\":\"safe-test-password\",\"rememberMe\":false}"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("refreshToken=").doesNotContain("Max-Age=");
    }

    @Test
    void loginRejectsWrongPasswordWithoutLeakingAccountExistence() throws Exception {
        // 계정은 존재해도 비밀번호가 틀리면 동일한 401 응답만 제공.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"admin@example.com\",\"password\":\"wrong-password\",\"rememberMe\":false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void meRejectsMissingAccessTokenWithCommonResponse() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void loginRejectsInvalidRequestWithCommonResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"not-an-email\",\"password\":\"\",\"rememberMe\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void loginRejectsMalformedJsonWithCommonResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void refreshRotatesTokenAndRejectsPreviouslyUsedToken() throws Exception {
        String previousRefreshToken = loginAndExtractRefreshToken();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthService.REFRESH_TOKEN_COOKIE_NAME, previousRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_REFRESHED"));

        // 이미 한 번 사용한 리프레시 토큰은 폐기되어 재사용할 수 없음.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthService.REFRESH_TOKEN_COOKIE_NAME, previousRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String refreshToken = loginAndExtractRefreshToken();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(AuthService.REFRESH_TOKEN_COOKIE_NAME, refreshToken)))
                .andExpect(status().isNoContent());

        // 로그아웃된 브라우저의 예전 쿠키는 새 액세스 토큰을 발급할 수 없음.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthService.REFRESH_TOKEN_COOKIE_NAME, refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void meRejectsExpiredAccessTokenWithCommonResponse() throws Exception {
        Instant now = Instant.now();
        String expiredAccessToken = jwtEncoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                .issuer("naverworks-clone-api-test")
                .subject("membership:" + testMembership.getId())
                .issuedAt(now.minusSeconds(600))
                // 기본 60초 clock skew를 넘겨 실제 만료 토큰으로 판단되게 설정.
                .expiresAt(now.minusSeconds(120))
                .claim("membershipId", testMembership.getId())
                .claim("companyId", testMembership.getCompany().getId())
                .claim("role", Role.ADMIN.name())
                .build())).getTokenValue();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private String loginAndExtractRefreshToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"admin@example.com\",\"password\":\"safe-test-password\",\"rememberMe\":false}"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotBlank();
        return setCookie.substring(
                (AuthService.REFRESH_TOKEN_COOKIE_NAME + "=").length(),
                setCookie.indexOf(';')
        );
    }

    // 테스트용 유효 액세스 토큰 발급(로그인 API를 거치지 않고 JWT만 만듦).
    private String accessTokenFor(Membership membership) {
        Instant now = Instant.now();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                .issuer("naverworks-clone-api-test")
                .subject("membership:" + membership.getId())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("membershipId", membership.getId())
                .claim("companyId", membership.getCompany().getId())
                .claim("role", membership.getRole().name())
                .build())).getTokenValue();
    }

    // 공개 가입: 체험 회사에 들어가고, 복구 코드와 로그인 세션을 함께 받음. 받은 아이디로 바로 로그인 가능.
    @Test
    void signupJoinsDemoCompanyAndReturnsRecoveryCode() throws Exception {
        MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"newbie01\",\"password\":\"safe-pass-123\",\"displayName\":\"신입\","
                                + "\"joinType\":\"DEMO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_SIGNUP_SUCCESS"))
                .andExpect(jsonPath("$.data.session.member.companyName").value("테스트 회사"))
                .andExpect(jsonPath("$.data.session.member.role").value("USER"))
                .andExpect(jsonPath("$.data.session.member.loginId").value("newbie01"))
                .andReturn();
        JsonNode body = objectMapper.readTree(signupResult.getResponse().getContentAsString());
        assertThat(body.path("data").path("recoveryCode").asText()).matches("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}");
        assertThat(signupResult.getResponse().getHeader("Set-Cookie")).contains("refreshToken=");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"newbie01\",\"password\":\"safe-pass-123\",\"rememberMe\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.displayName").value("신입"));
    }

    // 새 회사 만들기: 만든 사람이 관리자이자 대표.
    @Test
    void signupWithNewCompanyMakesOwnerAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"founder\",\"password\":\"safe-pass-123\",\"displayName\":\"창업자\","
                                + "\"joinType\":\"NEW_COMPANY\",\"companyName\":\"새 회사\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.member.companyName").value("새 회사"))
                .andExpect(jsonPath("$.data.session.member.role").value("ADMIN"));
        assertThat(companyRepository.existsByName("새 회사")).isTrue();
    }

    // 중복 아이디는 이유를 알려 주는 400.
    @Test
    void signupRejectsDuplicateLoginId() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"admin@example.com\",\"password\":\"safe-pass-123\","
                                + "\"displayName\":\"중복\",\"joinType\":\"DEMO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"newbie01\",\"password\":\"safe-pass-123\",\"displayName\":\"첫째\","
                                + "\"joinType\":\"DEMO\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"newbie01\",\"password\":\"safe-pass-123\",\"displayName\":\"둘째\","
                                + "\"joinType\":\"DEMO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_ID_TAKEN"));
    }

    // 초대 코드: 관리자가 발급한 코드로 가입하면 그 회사의 일반 구성원이 됨.
    @Test
    void signupWithInviteCodeJoinsAdminsCompany() throws Exception {
        String adminToken = accessTokenFor(testMembership);
        MvcResult inviteResult = mockMvc.perform(get("/api/v1/auth/company/inviteCode")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("테스트 회사"))
                .andReturn();
        String inviteCode = objectMapper.readTree(inviteResult.getResponse().getContentAsString())
                .path("data").path("inviteCode").asText();
        assertThat(inviteCode).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"invited\",\"password\":\"safe-pass-123\",\"displayName\":\"초대됨\","
                                + "\"joinType\":\"INVITE\",\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.member.companyName").value("테스트 회사"))
                .andExpect(jsonPath("$.data.session.member.role").value("USER"));

        // 일반 사용자는 초대 코드를 볼 수 없음.
        mockMvc.perform(get("/api/v1/auth/company/inviteCode")
                        .header("Authorization", "Bearer " + accessTokenFor(testUserMembership)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVITE_CODE_FORBIDDEN"));
    }
}
