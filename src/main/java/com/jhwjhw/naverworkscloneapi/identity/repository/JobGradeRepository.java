package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.JobGrade;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// 직급 데이터를 조회·정리할 때 사용하는 JPA Repository.
public interface JobGradeRepository extends JpaRepository<JobGrade, Long> {

    // 직급 조회
    List<JobGrade> findByCompanyIdOrderBySortOrderAsc(Long companyId);
}
