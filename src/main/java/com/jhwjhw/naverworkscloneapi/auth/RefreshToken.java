package com.jhwjhw.naverworkscloneapi.auth;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "refresh_token")
// @Data 대신 조회 전용 Getter만 생성해 JPA 연관관계의 위험한 Setter/toString 생성을 막음.
@Getter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_id", nullable = false, unique = true)
    private UUID tokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    // 원문 토큰 대신 SHA-256 해시만 보관.
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // 로그인 유지 선택 여부를 보관해 토큰 재발급 뒤에도 쿠키 성격을 유지.
    @Column(name = "persistent_login", nullable = false)
    private boolean persistentLogin;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshToken() {
        // JPA가 DB 조회 결과로 객체를 만들 때 사용하는 기본 생성자.
    }

    public RefreshToken(
            UUID tokenId,
            Membership membership,
            String tokenHash,
            Instant expiresAt,
            boolean persistentLogin
    ) {
        this.tokenId = tokenId;
        this.membership = membership;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.persistentLogin = persistentLogin;
        this.createdAt = Instant.now();
    }

    public boolean isUsableAt(Instant now) {
        // 폐기되었거나 만료된 토큰은 재발급에 사용할 수 없음.
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant revokedAt) {
        // 삭제 대신 폐기 시각을 남겨 탈취 토큰 재사용을 차단.
        this.revokedAt = revokedAt;
    }
}
