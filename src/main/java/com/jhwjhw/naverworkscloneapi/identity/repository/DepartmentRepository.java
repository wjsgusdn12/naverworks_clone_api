package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// 부서 데이터를 조회·정리할 때 사용하는 JPA Repository.
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // 부서 조회
    List<Department> findByCompanyIdOrderByNameAsc(Long companyId);

    // 조직도 한 번에 그릴 때 회사마다 따로 조회하지 않도록 전체를 한 번에 읽음.
    List<Department> findAllByOrderByNameAsc();
}
