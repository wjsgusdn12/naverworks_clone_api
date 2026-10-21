package com.jhwjhw.naverworkscloneapi.identity.domain;

import jakarta.persistence.Column;
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
@Table(name = "address_book_search_history")
@Getter
// 주소록 검색어를 사용자별 최신순으로 보관.
public class AddressBookSearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_membership_id", nullable = false)
    private Membership ownerMembership;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AddressBookSearchHistory() {
        // JPA 전용 기본 생성자.
    }

    public AddressBookSearchHistory(Membership ownerMembership, String keyword) {
        this.ownerMembership = ownerMembership;
        this.keyword = keyword;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
