package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.ContactGroupMember;
import com.jhwjhw.naverworkscloneapi.identity.domain.ContactGroupMember.ContactGroupMemberId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactGroupMemberRepository extends JpaRepository<ContactGroupMember, ContactGroupMemberId> {

    // 주소록 그룹 구성원 조회
    List<ContactGroupMember> findByContactGroupId(Long contactGroupId);

    // 주소록 그룹 구성원 개수 조회
    long countByContactGroupId(Long contactGroupId);

    // 주소록 그룹 구성원 존재 여부 조회
    boolean existsByContactGroupIdAndMembershipId(Long contactGroupId, Long membershipId);

    // 회사 옮기기 정리용: 내가 속한 주소록 그룹
    List<ContactGroupMember> findByMembershipId(Long membershipId);
}
