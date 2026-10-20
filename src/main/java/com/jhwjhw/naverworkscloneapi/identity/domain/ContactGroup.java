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
@Table(name = "contact_group")
@Getter
// 주소록 전용 그룹. 향후 메신저 채팅방과는 별도 도메인으로 연결.
public class ContactGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "master_membership_id", nullable = false)
    private Membership masterMembership;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContactGroup() {
        // JPA 전용 기본 생성자.
    }

    public ContactGroup(Company company, String name, Membership masterMembership) {
        this.company = company;
        this.name = name;
        this.masterMembership = masterMembership;
        this.createdAt = Instant.now();
    }

    public void rename(String name) {
        this.name = name;
    }
}
