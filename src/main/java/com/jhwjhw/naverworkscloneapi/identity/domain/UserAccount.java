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

    // 이메일
    @Column(nullable = false, length = 255, unique = true)
    private String email;

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

    // 사용자 계정 생성
    public UserAccount(String email, String passwordHash, String displayName) {
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

}
