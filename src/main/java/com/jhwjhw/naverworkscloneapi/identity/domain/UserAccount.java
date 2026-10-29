package com.jhwjhw.naverworkscloneapi.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "user_account")
@Getter
// 로그인 가능한 사용자 계정. 비밀번호 원문은 저장하지 않음.
public class UserAccount {

    // 사용자 계정 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 아이디. 기존 계정은 이메일 값을 그대로 아이디로 사용.
    @Column(name = "login_id", nullable = false, length = 100, unique = true)
    private String loginId;

    // 이메일. 공개 가입에서는 선택 입력이라 비어 있을 수 있음.
    @Column(length = 255, unique = true)
    private String email;

    // 비밀번호 재설정용 복구 코드의 SHA-256 해시. 원문은 저장하지 않음.
    @Column(name = "recovery_code_hash", length = 64)
    private String recoveryCodeHash;

    // 비밀번호 해시
    @Column(name = "password_hash", nullable = false, length = 100)
    // BCrypt로 단방향 해시한 비밀번호만 저장.
    private String passwordHash;

    // 표시 이름
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    // 활성 여부
    @Column(nullable = false)
    private boolean enabled;

    // 사용자 계정 생성 시간
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 사용자 계정 생성
    protected UserAccount() {
        // JPA 전용 기본 생성자.
    }

    // 이메일을 아이디로도 쓰는 계정 생성(기존 시드·테스트 호환).
    public UserAccount(String email, String passwordHash, String displayName) {
        this(email, email, passwordHash, displayName);
    }

    // 사용자 계정 생성
    public UserAccount(String loginId, String email, String passwordHash, String displayName) {
        this.loginId = loginId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    // 사용자 계정 프로필 수정
    public void updateProfile(String displayName, boolean enabled) {
        this.displayName = displayName;
        this.enabled = enabled;
    }

    // 비밀번호 변경(해시만 받음)
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    // 복구 코드 교체. 이전 코드는 즉시 쓸 수 없게 됨.
    public void updateRecoveryCodeHash(String recoveryCodeHash) {
        this.recoveryCodeHash = recoveryCodeHash;
    }

}
