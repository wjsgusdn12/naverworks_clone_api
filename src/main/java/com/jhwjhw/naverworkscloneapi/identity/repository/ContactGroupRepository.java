package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.ContactGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactGroupRepository extends JpaRepository<ContactGroup, Long> {

    // 주소록 그룹 조회
    List<ContactGroup> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}
