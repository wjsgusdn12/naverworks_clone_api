package com.jhwjhw.naverworkscloneapi.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 토큰 원문 대신 SHA-256 해시로 리프레시 토큰을 조회하는 리포지터리.
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // 토큰 해시로 리프레시 토큰 조회
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 비밀번호 재설정 시 기존 로그인을 모두 끊기 위해 구성원의 살아 있는 토큰을 조회
    List<RefreshToken> findByMembershipIdAndRevokedAtIsNull(Long membershipId);
}
