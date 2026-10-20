package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 사용자 계정이 속한 회사·부서·역할 정보를 조회.
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    // 초기 정책: 한 계정은 한 회사에만 소속되므로 단일 소속만 조회.
    Optional<Membership> findByUserAccountId(Long userAccountId);
}
