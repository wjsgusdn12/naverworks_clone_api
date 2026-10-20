package com.jhwjhw.naverworkscloneapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.domain.Role;
import com.jhwjhw.naverworkscloneapi.identity.domain.UserAccount;
import com.jhwjhw.naverworkscloneapi.identity.repository.CompanyRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.UserAccountRepository;

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
        membershipRepository.save(new Membership(company, userAccount, null, Role.ADMIN));
    }

    @Test
    void loginIssuesAccessTokenAndRefreshCookieThenMeReadsMember() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"safe-test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.member.role").value("ADMIN"))
                .andReturn();

        // 로그인 응답에서 액세스 토큰과 Set-Cookie 헤더를 각각 확인.
        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        JsonNode responseBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = responseBody.path("accessToken").asText();

        // 리프레시 토큰은 HttpOnly 쿠키, API 호출용 액세스 토큰은 응답 본문으로 분리.
        assertThat(setCookie).contains("refreshToken=").contains("HttpOnly");
        assertThat(accessToken).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void loginRejectsWrongPasswordWithoutLeakingAccountExistence() throws Exception {
        // 계정은 존재해도 비밀번호가 틀리면 동일한 401 응답만 제공.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }
}
