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
@Table(name = "job_grade")
@Getter
// 회사마다 관리하는 직급 코드. 구성원의 직책과 별개로 관리.
public class JobGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JobGrade() {
        // JPA 전용 기본 생성자.
    }

    public JobGrade(Company company, String name, int sortOrder) {
        this.company = company;
        this.name = name;
        this.sortOrder = sortOrder;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public void update(String name, int sortOrder, boolean enabled) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }
}
