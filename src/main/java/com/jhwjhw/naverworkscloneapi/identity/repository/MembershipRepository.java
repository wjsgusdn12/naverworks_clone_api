package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 사용자 계정이 속한 회사·부서·역할 정보를 조회.
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    // 현재 초기 모델은 계정당 첫 번째 소속을 로그인 소속으로 사용.
    Optional<Membership> findFirstByUserAccountIdOrderByIdAsc(Long userAccountId);
}
