package com.jhwjhw.naverworkscloneapi.identity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "member_favorite")
@Getter
// 주소록 즐겨찾기는 소유 구성원별로 따로 보관.
public class MemberFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_membership_id", nullable = false)
    private Membership ownerMembership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_membership_id", nullable = false)
    private Membership targetMembership;

    private Instant createdAt;

    protected MemberFavorite() {
        // JPA 전용 기본 생성자.
    }

    public MemberFavorite(Membership ownerMembership, Membership targetMembership) {
        this.ownerMembership = ownerMembership;
        this.targetMembership = targetMembership;
        this.createdAt = Instant.now();
    }
}
