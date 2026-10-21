package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.MemberFavorite;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 즐겨찾기 데이터를 조회·정리할 때 사용하는 JPA Repository.
public interface MemberFavoriteRepository extends JpaRepository<MemberFavorite, Long> {

    // 즐겨찾기 조회
    Optional<MemberFavorite> findByOwnerMembershipIdAndTargetMembershipId(Long ownerMembershipId, Long targetMembershipId);

    // 즐겨찾기 조회
    List<MemberFavorite> findByOwnerMembershipId(Long ownerMembershipId);

    // 즐겨찾기 조회
    List<MemberFavorite> findByOwnerMembershipIdAndTargetMembershipIdIn(Long ownerMembershipId, Collection<Long> targetMembershipIds);
}
