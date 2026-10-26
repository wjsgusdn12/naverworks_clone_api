package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.domain.Specification;

// 사용자 계정이 속한 회사·부서·역할 정보를 조회·정리할 때 사용하는 JPA Repository.
public interface MembershipRepository extends JpaRepository<Membership, Long>, JpaSpecificationExecutor<Membership> {

    // 초기 정책: 한 계정은 한 회사에만 소속되므로 단일 소속만 조회.
    Optional<Membership> findByUserAccountId(Long userAccountId);

    // WebSocket 이벤트에서는 요청 트랜잭션이 없으므로 표시 이름을 함께 조회.
    @EntityGraph(attributePaths = {"userAccount"})
    Optional<Membership> findWithUserAccountById(Long membershipId);

    // 주소록 한 행을 만드는 데 필요한 다대일 관계를 한 번에 읽어 N+1 조회를 방지.
    @Override
    @EntityGraph(attributePaths = {"company", "userAccount", "department", "jobGrade"})
    Page<Membership> findAll(Specification<Membership> specification, Pageable pageable);

    // 부서 존재 여부 조회
    boolean existsByDepartmentIdAndUserAccountEnabledTrue(Long departmentId);
}
