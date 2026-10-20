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
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.UserAccountRepository;
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

@SpringBootTest
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
    private UserAccountRepository userAccountRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    private Membership testMembership;

    @BeforeEach
    void setUp() {
        // 테스트끼리 데이터가 섞이지 않도록 FK 순서의 역순으로 초기화.
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        userAccountRepository.deleteAll();
        companyRepository.deleteAll();

        // 운영 계정과 무관한 테스트 전용 관리자 계정 생성.
        Company company = companyRepository.save(new Company("테스트 회사"));
        UserAccount userAccount = userAccountRepository.save(new UserAccount(
                "admin@example.com",
                passwordEncoder.encode("safe-test-password"),
                "테스트 관리자"
        ));
        testMembership = membershipRepository.save(new Membership(company, userAccount, null, Role.ADMIN));
    }

    @Test
    void loginIssuesAccessTokenAndRefreshCookieThenMeReadsMember() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"safe-test-password\"}"))
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
        assertThat(setCookie).contains("refreshToken=").contains("HttpOnly");
        assertThat(accessToken).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_MEMBER_FOUND"))
                .andExpect(jsonPath("$.data.email").value("admin@example.com"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void loginRejectsWrongPasswordWithoutLeakingAccountExistence() throws Exception {
        // 계정은 존재해도 비밀번호가 틀리면 동일한 401 응답만 제공.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"wrong-password\"}"))
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
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void loginRejectsMalformedJsonWithCommonResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
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
                        .content("{\"email\":\"admin@example.com\",\"password\":\"safe-test-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotBlank();
        return setCookie.substring(
                (AuthService.REFRESH_TOKEN_COOKIE_NAME + "=").length(),
                setCookie.indexOf(';')
        );
    }
}
