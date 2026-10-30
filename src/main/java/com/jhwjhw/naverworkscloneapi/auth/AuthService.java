package com.jhwjhw.naverworkscloneapi.auth;

import com.jhwjhw.naverworkscloneapi.chat.ChatRoomService;
import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import com.jhwjhw.naverworkscloneapi.identity.domain.Department;
import com.jhwjhw.naverworkscloneapi.identity.domain.JobGrade;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.domain.Role;
import com.jhwjhw.naverworkscloneapi.identity.domain.UserAccount;
import com.jhwjhw.naverworkscloneapi.identity.repository.CompanyRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.ContactGroupMemberRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.DepartmentRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.JobGradeRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MemberFavoriteRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.UserAccountRepository;
import com.jhwjhw.naverworkscloneapi.notification.NotificationService;
import com.jhwjhw.naverworkscloneapi.notification.domain.NotificationCategory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jhwjhw.naverworkscloneapi.common.privacy.PersonalInfoMasker;

@Service
// final 의존성 생성자는 Lombok이 자동 생성.
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final UserAccountRepository userAccountRepository;
    private final MembershipRepository membershipRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final JobGradeRepository jobGradeRepository;
    private final MemberFavoriteRepository memberFavoriteRepository;
    private final ContactGroupMemberRepository contactGroupMemberRepository;
    private final ChatRoomService chatRoomService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AppAuthProperties appAuthProperties;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    // 로그인. userAgent는 "어떤 브라우저에서 로그인했는지" 시스템 알림에만 사용.
    @Transactional
    public AuthResult login(AuthRequest request, String userAgent) {
        // 아이디로 먼저 찾고 없으면 이메일로. 계정 없음·비활성·비밀번호 불일치는 같은 응답으로 처리해 존재 여부를 숨김.
        String identifier = normalizeEmail(request.loginId());
        UserAccount userAccount = userAccountRepository.findByLoginId(identifier)
                .or(() -> userAccountRepository.findByEmail(identifier))
                .filter(UserAccount::isEnabled)
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(InvalidAuthenticationException::new);

        // DB 유니크 제약으로 보장되는 단일 회사 소속을 인증 주체로 사용.
        Membership membership = membershipRepository.findByUserAccountId(userAccount.getId())
                .orElseThrow(InvalidAuthenticationException::new);

        // 비밀번호·토큰·이메일은 남기지 않고 인증 주체 식별자만 운영 로그에 기록.
        log.info("Login succeeded. membershipId={}", membership.getId());
        // 네이버웍스처럼 새 로그인을 시스템 알림으로 남겨 본인이 아닌 접속을 알아차릴 수 있게 함.
        notificationService.create(membership.getId(), NotificationCategory.SYSTEM, "로그인 알림",
                "새로운 브라우저(" + browserName(userAgent) + ")에서 로그인하였습니다.", null);
        return issueTokens(membership, request.rememberMe(), Instant.now().plus(appAuthProperties.refreshTokenExpiration()));
    }

    // 토큰 갱신
    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        RefreshToken refreshToken = findUsableRefreshToken(rawRefreshToken);
        // 토큰을 한 번 쓰면 즉시 폐기하는 Rotation 방식으로 탈취 토큰 재사용을 줄임.
        refreshToken.revoke(Instant.now());
        log.debug("Refresh token rotated. membershipId={}", refreshToken.getMembership().getId());
        // Rotation 후에도 최초 로그인에서 정한 전체 세션 만료 시각은 연장하지 않음.
        return issueTokens(refreshToken.getMembership(), refreshToken.isPersistentLogin(), refreshToken.getExpiresAt());
    }

    // 현재 로그인 구성원 정보 조회
    @Transactional(readOnly = true)
    public AuthMemberResponse getCurrentMember(Long membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(InvalidAuthenticationException::new);
        return toMemberResponse(membership);
    }

    // 로그아웃
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)).ifPresent(token -> {
            token.revoke(Instant.now());
            log.info("Logout succeeded. membershipId={}", token.getMembership().getId());
        });
    }

    // 리프레시 토큰 쿠키 생성
    public ResponseCookie createRefreshCookie(String rawRefreshToken, boolean persistentLogin, Instant sessionExpiresAt) {
        // HttpOnly라 브라우저 JavaScript로 읽을 수 없어 XSS 노출 범위를 줄임.
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie
                .from(REFRESH_TOKEN_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(appAuthProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth");

        if (persistentLogin) {
            // Rotation 뒤에도 최초 로그인 기준 남은 시간만큼만 영속 쿠키를 유지.
            cookieBuilder.maxAge(Duration.between(Instant.now(), sessionExpiresAt));
        }

        return cookieBuilder.build();
    }

    // 리프레시 토큰 쿠키 삭제
    public ResponseCookie deleteRefreshCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(appAuthProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    // 가입 화면용: 체험 회사 이름과 부서·직급 목록.
    @Transactional(readOnly = true)
    public SignupResponse.Options signupOptions() {
        Company demo = demoCompany();
        return new SignupResponse.Options(demo.getName(),
                departmentRepository.findByCompanyIdOrderByNameAsc(demo.getId()).stream()
                        .filter(Department::isEnabled)
                        .map(department -> new SignupResponse.Option(department.getId(), department.getName()))
                        .toList(),
                jobGradeRepository.findByCompanyIdOrderBySortOrderAsc(demo.getId()).stream()
                        .filter(JobGrade::isEnabled)
                        // "대표"는 새 회사를 만든 사람만 가질 수 있어 선택지에서 뺌.
                        .filter(grade -> !OWNER_GRADE_NAME.equals(grade.getName()))
                        .map(grade -> new SignupResponse.Option(grade.getId(), grade.getName()))
                        .toList());
    }

    // 새 회사를 만든 사람만 가질 수 있는 직급. 가입·회사 옮기기에서 직접 고를 수 없음.
    private static final String OWNER_GRADE_NAME = "대표";
    // 아무 선택도 없을 때 쓰는 기본 부서·직급 이름.
    private static final String DEFAULT_DEPARTMENT_NAME = "경영팀";
    private static final String DEFAULT_GRADE_NAME = "사원";

    // 공개 가입. 체험 회사 참여 / 새 회사 만들기(대표) / 초대 코드 참여 중 하나로 소속을 정하고 바로 로그인 세션을 발급.
    @Transactional
    public SignupResult signup(SignupRequest request, String userAgent) {
        String loginId = request.loginId().trim().toLowerCase(Locale.ROOT);
        String email = request.email() == null || request.email().isBlank() ? null : normalizeEmail(request.email());
        if (userAccountRepository.existsByLoginId(loginId)) {
            throw new AuthException("AUTH_LOGIN_ID_TAKEN", "이미 사용 중인 아이디입니다.");
        }
        if (email != null && userAccountRepository.existsByEmail(email)) {
            throw new AuthException("AUTH_EMAIL_TAKEN", "이미 등록된 이메일입니다.");
        }

        UserAccount userAccount = new UserAccount(loginId, email, passwordEncoder.encode(request.password()),
                request.displayName().trim());
        String recoveryCode = createRecoveryCode();
        userAccount.updateRecoveryCodeHash(hashToken(recoveryCode));
        userAccountRepository.save(userAccount);

        Placement placement = placement(request.joinType(), request.departmentId(), request.jobGradeId(),
                request.companyName(), request.inviteCode());
        Membership membership = new Membership(placement.company(), userAccount, placement.department(),
                placement.role());
        String phone = request.phoneNumber() == null || request.phoneNumber().isBlank() ? null
                : request.phoneNumber().trim();
        membership.updateProfile(placement.department(), placement.jobGrade(), placement.jobTitle(), phone, null,
                placement.role());
        membershipRepository.save(membership);

        log.info("Signup succeeded. membershipId={}, companyId={}, joinType={}", membership.getId(),
                membership.getCompany().getId(), request.joinType());
        notificationService.create(membership.getId(), NotificationCategory.SYSTEM, "가입을 환영합니다",
                "복구 코드는 내 프로필에서 다시 확인할 수 있습니다. 브라우저: " + browserName(userAgent), null);
        AuthResult session = issueTokens(membership, false, Instant.now().plus(appAuthProperties.refreshTokenExpiration()));
        return new SignupResult(session, recoveryCode);
    }

    // 어느 회사에 어떤 소속으로 들어갈지. 가입과 회사 옮기기가 함께 씀.
    private record Placement(Company company, Department department, JobGrade jobGrade, String jobTitle, Role role) {
    }

    private Placement placement(SignupRequest.JoinType joinType, Long departmentId, Long jobGradeId,
            String companyName, String inviteCode) {
        return switch (joinType) {
            case DEMO -> demoPlacement(departmentId, jobGradeId);
            case NEW_COMPANY -> newCompanyPlacement(companyName);
            case INVITE -> invitePlacement(inviteCode);
        };
    }

    // 체험 회사 참여. 부서·직급은 체험 회사 것만 허용하고, 고르지 않으면 기본값(첫 부서·사원)을 넣음.
    private Placement demoPlacement(Long departmentId, Long jobGradeId) {
        Company demo = demoCompany();
        Department department = departmentId == null ? defaultDepartment(demo)
                : departmentRepository.findById(departmentId)
                        .filter(found -> found.getCompany().getId().equals(demo.getId()) && found.isEnabled())
                        .orElseThrow(() -> new AuthException("AUTH_DEPARTMENT_INVALID", "선택한 부서를 찾을 수 없습니다."));
        JobGrade jobGrade = jobGradeId == null ? defaultJobGrade(demo)
                : jobGradeRepository.findById(jobGradeId)
                        .filter(found -> found.getCompany().getId().equals(demo.getId()) && found.isEnabled())
                        .orElseThrow(() -> new AuthException("AUTH_JOB_GRADE_INVALID", "선택한 직급을 찾을 수 없습니다."));
        if (OWNER_GRADE_NAME.equals(jobGrade.getName())) {
            // 대표는 새 회사를 직접 만든 사람만 될 수 있음.
            throw new AuthException("AUTH_JOB_GRADE_INVALID", "대표 직급은 새 회사를 만들 때만 가질 수 있습니다.");
        }
        return new Placement(demo, department, jobGrade, null, Role.USER);
    }

    // 새 회사 만들기. 만든 사람이 관리자이자 "대표" 직급·직책을 가짐. 기본 부서(경영팀)와 직급(대표·사원)을 함께 생성.
    private Placement newCompanyPlacement(String companyName) {
        String name = companyName == null ? "" : companyName.trim();
        if (name.isEmpty()) {
            throw new AuthException("AUTH_COMPANY_NAME_REQUIRED", "회사 이름을 입력해 주세요.");
        }
        if (companyRepository.existsByName(name)) {
            throw new AuthException("AUTH_COMPANY_NAME_TAKEN", "이미 있는 회사 이름입니다.");
        }
        Company company = companyRepository.save(new Company(name));
        Department department = departmentRepository.save(new Department(company, DEFAULT_DEPARTMENT_NAME));
        JobGrade owner = jobGradeRepository.save(new JobGrade(company, OWNER_GRADE_NAME, 1));
        jobGradeRepository.save(new JobGrade(company, DEFAULT_GRADE_NAME, 2));
        return new Placement(company, department, owner, OWNER_GRADE_NAME, Role.ADMIN);
    }

    // 초대 코드로 참여. 코드는 관리자가 발급한 것만 유효. 소속은 그 회사의 기본 부서·사원으로 시작.
    private Placement invitePlacement(String inviteCode) {
        String code = inviteCode == null ? "" : inviteCode.trim().toUpperCase(Locale.ROOT);
        Company company = companyRepository.findByInviteCode(code)
                .orElseThrow(() -> new AuthException("AUTH_INVITE_CODE_INVALID", "초대 코드가 올바르지 않습니다."));
        return new Placement(company, defaultDepartment(company), defaultJobGrade(company), null, Role.USER);
    }

    // 기본 부서: 이름순 첫 부서. 하나도 없으면 "경영팀"을 만들어 줌.
    private Department defaultDepartment(Company company) {
        return departmentRepository.findByCompanyIdOrderByNameAsc(company.getId()).stream()
                .filter(Department::isEnabled)
                .findFirst()
                .orElseGet(() -> departmentRepository.save(new Department(company, DEFAULT_DEPARTMENT_NAME)));
    }

    // 기본 직급: "사원"이 있으면 그것, 없으면 대표를 뺀 첫 직급. 그마저 없으면 "사원"을 만들어 줌.
    private JobGrade defaultJobGrade(Company company) {
        var grades = jobGradeRepository.findByCompanyIdOrderBySortOrderAsc(company.getId()).stream()
                .filter(JobGrade::isEnabled)
                .filter(grade -> !OWNER_GRADE_NAME.equals(grade.getName()))
                .toList();
        return grades.stream()
                .filter(grade -> DEFAULT_GRADE_NAME.equals(grade.getName()))
                .findFirst()
                .or(() -> grades.stream().findFirst())
                .orElseGet(() -> jobGradeRepository.save(new JobGrade(company, DEFAULT_GRADE_NAME,
                        jobGradeRepository.findByCompanyIdOrderBySortOrderAsc(company.getId()).size() + 1)));
    }

    // 회사 옮기기. 이전 회사의 채팅방·즐겨찾기·주소록 그룹을 모두 정리한 뒤 소속을 바꾸고, 회사 ID가 든 토큰을 새로 발급.
    @Transactional
    public AuthResult moveCompany(Long membershipId, CompanyMoveRequest request) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(InvalidAuthenticationException::new);
        Placement placement = placement(request.joinType(), request.departmentId(), request.jobGradeId(),
                request.companyName(), request.inviteCode());
        if (placement.company().getId().equals(membership.getCompany().getId())) {
            throw new AuthException("AUTH_COMPANY_SAME", "이미 그 회사에 속해 있습니다.");
        }
        Long previousCompanyId = membership.getCompany().getId();

        chatRoomService.leaveAllForCompanyMove(membershipId);
        memberFavoriteRepository.deleteAll(memberFavoriteRepository.findByOwnerMembershipId(membershipId));
        memberFavoriteRepository.deleteAll(memberFavoriteRepository.findByTargetMembershipId(membershipId));
        contactGroupMemberRepository.deleteAll(contactGroupMemberRepository.findByMembershipId(membershipId));

        membership.moveTo(placement.company(), placement.department(), placement.jobGrade(), placement.jobTitle(),
                placement.role());
        // 이전 회사 ID가 든 토큰은 모두 끊고 새 토큰을 발급.
        Instant now = Instant.now();
        refreshTokenRepository.findByMembershipIdAndRevokedAtIsNull(membershipId).forEach(token -> token.revoke(now));
        notificationService.create(membershipId, NotificationCategory.SYSTEM, "회사 이동",
                placement.company().getName() + " 소속으로 옮겼습니다.", null);
        log.info("Company moved. membershipId={}, fromCompanyId={}, toCompanyId={}, joinType={}", membershipId,
                previousCompanyId, placement.company().getId(), request.joinType());
        return issueTokens(membership, false, now.plus(appAuthProperties.refreshTokenExpiration()));
    }

    // 복구 코드 재발급. 이전 코드는 즉시 무효.
    @Transactional
    public SignupResponse.RecoveryCode regenerateRecoveryCode(Long membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(InvalidAuthenticationException::new);
        String recoveryCode = createRecoveryCode();
        membership.getUserAccount().updateRecoveryCodeHash(hashToken(recoveryCode));
        log.info("Recovery code regenerated. membershipId={}", membershipId);
        return new SignupResponse.RecoveryCode(recoveryCode);
    }

    // 회사 초대 코드. 관리자만 볼 수 있고, 처음 볼 때 발급.
    @Transactional
    public SignupResponse.InviteCode companyInviteCode(Long membershipId, boolean regenerate) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(InvalidAuthenticationException::new);
        if (membership.getRole() != Role.ADMIN) {
            throw new AuthException("AUTH_INVITE_CODE_FORBIDDEN", "초대 코드는 관리자만 볼 수 있습니다.");
        }
        Company company = membership.getCompany();
        if (company.getInviteCode() == null || regenerate) {
            company.issueInviteCode(createInviteCode());
            log.info("Company invite code issued. companyId={}", company.getId());
        }
        return new SignupResponse.InviteCode(company.getName(), company.getInviteCode());
    }

    // 계정 찾기 1단계: 복구 코드가 있으면 코드로, 없으면 본인 정보(이름·이메일·전화번호)로 계정을 확인.
    // 어느 쪽이든 맞으면 가린 아이디와 이름을 알려 주고 바로 비밀번호를 바꿀 수 있음.
    @Transactional(readOnly = true)
    public SignupResponse.RecoverLookup recoverLookup(RecoverRequest.Lookup request) {
        UserAccount userAccount = recoverAccount(request.displayName(), request.email(), request.phoneNumber(),
                request.recoveryCode());
        return new SignupResponse.RecoverLookup(maskLoginId(userAccount.getLoginId()), userAccount.getDisplayName(),
                true);
    }

    // 계정 찾기 2단계: 1단계와 같은 정보로 다시 확인한 뒤 비밀번호를 바꾸고 기존 로그인을 모두 끊음.
    // 쓴 복구 코드는 버리고 새 코드를 발급.
    @Transactional
    public SignupResponse.RecoveryCode recoverReset(RecoverRequest.Reset request) {
        boolean byRecoveryCode = request.recoveryCode() != null && !request.recoveryCode().isBlank();
        UserAccount userAccount = recoverAccount(request.displayName(), request.email(), request.phoneNumber(),
                request.recoveryCode());
        userAccount.changePassword(passwordEncoder.encode(request.newPassword()));
        String nextCode = createRecoveryCode();
        userAccount.updateRecoveryCodeHash(hashToken(nextCode));
        membershipRepository.findByUserAccountId(userAccount.getId()).ifPresent(membership -> {
            Instant now = Instant.now();
            refreshTokenRepository.findByMembershipIdAndRevokedAtIsNull(membership.getId())
                    .forEach(token -> token.revoke(now));
            String method = byRecoveryCode ? "복구 코드" : "본인 정보 확인";
            notificationService.create(membership.getId(), NotificationCategory.SYSTEM, "비밀번호 변경 알림",
                    method + "으로 비밀번호가 변경되었습니다. 본인이 아니라면 다시 변경해 주세요.", null);
            log.info("Password reset. membershipId={}, method={}", membership.getId(),
                    byRecoveryCode ? "RECOVERY_CODE" : "IDENTITY");
        });
        return new SignupResponse.RecoveryCode(nextCode);
    }

    // 복구 코드가 있으면 코드로, 없으면 본인 정보 세 가지로 계정을 찾음.
    private UserAccount recoverAccount(String displayName, String email, String phoneNumber, String recoveryCode) {
        if (recoveryCode != null && !recoveryCode.isBlank()) {
            return accountByRecoveryCode(recoveryCode);
        }
        if (isBlank(displayName) || isBlank(email) || isBlank(phoneNumber)) {
            throw new AuthException("AUTH_RECOVER_INPUT_REQUIRED", "이름·이메일·전화번호를 모두 입력하거나 복구 코드를 입력해 주세요.");
        }
        return accountByIdentity(displayName, email, phoneNumber);
    }

    // 복구 코드 해시로 계정을 찾음.
    private UserAccount accountByRecoveryCode(String recoveryCode) {
        String normalizedCode = recoveryCode.trim().toUpperCase(Locale.ROOT);
        return userAccountRepository.findByRecoveryCodeHash(hashToken(normalizedCode))
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new AuthException("AUTH_RECOVERY_CODE_INVALID", "복구 코드가 올바르지 않습니다."));
    }

    // 이메일로 계정을 찾고 이름·전화번호까지 모두 같아야 통과. 전화번호는 하이픈·공백을 빼고 숫자만 비교.
    // 어느 항목이 틀렸는지는 알려 주지 않아 가입 여부를 떠보기 어렵게 함.
    private UserAccount accountByIdentity(String displayName, String email, String phoneNumber) {
        String name = displayName.trim();
        String phone = PersonalInfoMasker.phoneDigits(phoneNumber);
        return userAccountRepository.findByEmail(normalizeEmail(email))
                .filter(UserAccount::isEnabled)
                .filter(account -> name.equals(account.getDisplayName()))
                .filter(account -> membershipRepository.findByUserAccountId(account.getId())
                        .map(membership -> PersonalInfoMasker.phoneDigits(membership.getPhoneNumber()))
                        .filter(saved -> !saved.isEmpty() && saved.equals(phone))
                        .isPresent())
                .orElseThrow(() -> new AuthException("AUTH_IDENTITY_MISMATCH",
                        "입력한 이름·이메일·전화번호와 일치하는 계정이 없습니다."));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 앞 2글자와 뒤 2글자만 남기고 가림. 짧은 아이디는 앞 1글자만.
    static String maskLoginId(String loginId) {
        if (loginId.length() <= 4) {
            return loginId.charAt(0) + "*".repeat(loginId.length() - 1);
        }
        return loginId.substring(0, 2) + "*".repeat(loginId.length() - 4) + loginId.substring(loginId.length() - 2);
    }

    private Company demoCompany() {
        return companyRepository.findFirstByDemoTrue()
                .orElseThrow(() -> new AuthException("AUTH_DEMO_COMPANY_MISSING", "체험 회사가 설정되어 있지 않습니다."));
    }

    // 헷갈리는 글자(0·O·1·I)를 뺀 알파벳으로 XXXX-XXXX-XXXX-XXXX 형태의 복구 코드 생성.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private String createRecoveryCode() {
        return randomCode(16, 4);
    }

    private String createInviteCode() {
        return randomCode(8, 4);
    }

    private String randomCode(int length, int groupSize) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < length; index++) {
            if (index > 0 && index % groupSize == 0) {
                builder.append('-');
            }
            builder.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    // 토큰 발급
    private AuthResult issueTokens(Membership membership, boolean persistentLogin, Instant sessionExpiresAt) {
        Instant now = Instant.now();
        // Access Token이 Refresh Token의 절대 만료 시각을 넘지 않게 제한.
        Instant configuredAccessTokenExpiresAt = now.plus(appAuthProperties.accessTokenExpiration());
        Instant accessTokenExpiresAt = configuredAccessTokenExpiresAt.isAfter(sessionExpiresAt)
                ? sessionExpiresAt
                : configuredAccessTokenExpiresAt;
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                .issuer(appAuthProperties.issuer())
                .subject("membership:" + membership.getId())
                .issuedAt(now)
                .expiresAt(accessTokenExpiresAt)
                // 이후 API 인가 시 현재 사용자와 회사·역할을 식별하는 최소 클레임.
                .claim("membershipId", membership.getId())
                .claim("companyId", membership.getCompany().getId())
                .claim("role", membership.getRole().name())
                .build())).getTokenValue();

        // DB에는 원문이 아닌 해시만 저장하고, 원문은 지금 응답 쿠키에만 사용.
        String rawRefreshToken = createRandomToken();
        refreshTokenRepository.save(new RefreshToken(
                UUID.randomUUID(),
                membership,
                hashToken(rawRefreshToken),
                sessionExpiresAt,
                persistentLogin));

        AuthResponse response = new AuthResponse(
                accessToken,
                "Bearer",
                Duration.between(now, accessTokenExpiresAt).toSeconds(),
                sessionExpiresAt,
                toMemberResponse(membership));
        return new AuthResult(response, rawRefreshToken, persistentLogin, sessionExpiresAt);
    }

    // 사용 가능한 리프레시 토큰 조회
    private RefreshToken findUsableRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidAuthenticationException();
        }

        return refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .filter(token -> token.isUsableAt(Instant.now()))
                .orElseThrow(InvalidAuthenticationException::new);
    }

    // User-Agent 문자열에서 대표 브라우저 이름만 추출. 순서가 중요: Edge·Opera는 "Chrome"도 포함함.
    private static String browserName(String userAgent) {
        String agent = userAgent == null ? "" : userAgent;
        if (agent.contains("Edg/")) return "Edge";
        if (agent.contains("OPR/")) return "Opera";
        if (agent.contains("Whale/")) return "Whale";
        if (agent.contains("Firefox/")) return "Firefox";
        if (agent.contains("Chrome/")) return "Chrome";
        if (agent.contains("Safari/")) return "Safari";
        return "알 수 없는 브라우저";
    }

    // 이메일 정규화
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // 랜덤 토큰 생성
    private String createRandomToken() {
        // 예측하기 어려운 난수 48바이트를 URL에 안전한 문자열로 변환.
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // 토큰 해시
    private String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", exception);
        }
    }

    // 구성원 정보 응답 생성
    private AuthMemberResponse toMemberResponse(Membership membership) {
        UserAccount userAccount = membership.getUserAccount();
        return new AuthMemberResponse(
                membership.getId(),
                membership.getCompany().getId(),
                membership.getCompany().getName(),
                userAccount.getId(),
                userAccount.getLoginId(),
                // 화면에는 가린 이메일만 내보냄.
                PersonalInfoMasker.maskEmail(userAccount.getEmail()),
                userAccount.getDisplayName(),
                membership.getRole().name());
    }

    // Controller에 응답 본문과 HttpOnly 쿠키 값을 함께 전달하는 서비스 내부 결과 객체.
    public record AuthResult(AuthResponse response, String refreshToken, boolean persistentLogin, Instant sessionExpiresAt) {
    }

    // 가입 결과: 로그인 세션 + 한 번만 보여 줄 복구 코드 원문.
    public record SignupResult(AuthResult session, String recoveryCode) {
    }
}
