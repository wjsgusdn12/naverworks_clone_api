package com.jhwjhw.naverworkscloneapi.identity.repository;

import com.jhwjhw.naverworkscloneapi.identity.domain.MemberFavorite;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberFavoriteRepository extends JpaRepository<MemberFavorite, Long> {

    Optional<MemberFavorite> findByOwnerMembershipIdAndTargetMembershipId(Long ownerMembershipId, Long targetMembershipId);

    // 즐겨찾기 전용 화면은 전체 구성원을 읽지 않고 이 사용자 행만 조회.
    List<MemberFavorite> findByOwnerMembershipId(Long ownerMembershipId);

    List<MemberFavorite> findByOwnerMembershipIdAndTargetMembershipIdIn(Long ownerMembershipId, Collection<Long> targetMembershipIds);
}
