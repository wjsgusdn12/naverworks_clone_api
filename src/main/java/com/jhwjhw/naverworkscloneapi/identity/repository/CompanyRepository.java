package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// Company 테이블의 기본 CRUD를 Spring Data JPA가 자동 구현.
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // 조직도는 회사명을 기준으로 안정적으로 같은 순서로 보여 줌.
    List<Company> findAllByOrderByNameAsc();
}
