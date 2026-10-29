package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// Company 테이블의 기본 CRUD를 Spring Data JPA가 자동 구현.
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // 조직도는 회사명을 기준으로 안정적으로 같은 순서로 보여 줌.
    List<Company> findAllByOrderByNameAsc();

    // 공개 가입자가 기본으로 들어가는 체험 회사
    Optional<Company> findFirstByDemoTrue();

    // 초대 코드로 회사 찾기
    Optional<Company> findByInviteCode(String inviteCode);

    // 새 회사 이름 중복 검사
    boolean existsByName(String name);
}
