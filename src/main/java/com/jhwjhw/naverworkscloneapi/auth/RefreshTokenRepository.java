package com.jhwjhw.naverworkscloneapi.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 토큰 원문 대신 SHA-256 해시로 리프레시 토큰을 조회.
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
