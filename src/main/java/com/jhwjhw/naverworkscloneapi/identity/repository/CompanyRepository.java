package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

// Company 테이블의 기본 CRUD를 Spring Data JPA가 자동 구현.
public interface CompanyRepository extends JpaRepository<Company, Long> {
}
