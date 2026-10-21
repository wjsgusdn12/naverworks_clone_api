package com.jhwjhw.naverworkscloneapi.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 토큰 원문 대신 SHA-256 해시로 리프레시 토큰을 조회하는 리포지터리.
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // 토큰 해시로 리프레시 토큰 조회
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
