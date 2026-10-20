package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.ContactGroupMember;
import com.jhwjhw.naverworkscloneapi.identity.domain.ContactGroupMember.ContactGroupMemberId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactGroupMemberRepository extends JpaRepository<ContactGroupMember, ContactGroupMemberId> {

    List<ContactGroupMember> findByContactGroupId(Long contactGroupId);

    long countByContactGroupId(Long contactGroupId);

    boolean existsByContactGroupIdAndMembershipId(Long contactGroupId, Long membershipId);
}
