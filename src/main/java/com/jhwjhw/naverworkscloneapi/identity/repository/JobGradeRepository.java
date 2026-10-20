package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.JobGrade;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobGradeRepository extends JpaRepository<JobGrade, Long> {

    List<JobGrade> findByCompanyIdOrderBySortOrderAsc(Long companyId);
}
