package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 사용자 계정 데이터를 조회·정리할 때 사용하는 JPA Repository.
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    // 이메일 조회
    Optional<UserAccount> findByEmail(String email);

    // 아이디 조회
    Optional<UserAccount> findByLoginId(String loginId);

    // 가입 시 중복 검사
    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    // 복구 코드 해시로 계정 찾기(비밀번호 재설정)
    Optional<UserAccount> findByRecoveryCodeHash(String recoveryCodeHash);
}
