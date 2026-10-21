package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.AddressBookSearchHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressBookSearchHistoryRepository extends JpaRepository<AddressBookSearchHistory, Long> {

    // 주소록 검색어 기록 조회
    List<AddressBookSearchHistory> findByOwnerMembershipIdOrderByUpdatedAtDesc(Long ownerMembershipId);

    // 주소록 검색어 기록 조회
    Optional<AddressBookSearchHistory> findByOwnerMembershipIdAndKeyword(Long ownerMembershipId, String keyword);

    // 주소록 검색어 기록 조회
    Optional<AddressBookSearchHistory> findByIdAndOwnerMembershipId(Long id, Long ownerMembershipId);

    // 주소록 검색어 기록 삭제
    void deleteByOwnerMembershipId(Long ownerMembershipId);
}
