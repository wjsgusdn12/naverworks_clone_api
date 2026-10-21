package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// 부서 데이터를 조회·정리할 때 사용하는 JPA Repository.
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // 부서 조회
    List<Department> findByCompanyIdOrderByNameAsc(Long companyId);
}
