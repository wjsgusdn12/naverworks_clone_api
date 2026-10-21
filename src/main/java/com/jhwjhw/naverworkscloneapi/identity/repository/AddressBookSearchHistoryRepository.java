package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.AddressBookSearchHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressBookSearchHistoryRepository extends JpaRepository<AddressBookSearchHistory, Long> {

    List<AddressBookSearchHistory> findByOwnerMembershipIdOrderByUpdatedAtDesc(Long ownerMembershipId);

    Optional<AddressBookSearchHistory> findByOwnerMembershipIdAndKeyword(Long ownerMembershipId, String keyword);

    Optional<AddressBookSearchHistory> findByIdAndOwnerMembershipId(Long id, Long ownerMembershipId);

    void deleteByOwnerMembershipId(Long ownerMembershipId);
}
