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

    // 직급 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회사
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // 직급 이름
    @Column(nullable = false, length = 50)
    private String name;

    // 직급 순서
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // 직급 활성 여부
    @Column(nullable = false)
    private boolean enabled;

    // 직급 생성 시간
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 직급 생성
    protected JobGrade() {
        // JPA 전용 기본 생성자.
    }

    // 직급 생성
    public JobGrade(Company company, String name, int sortOrder) {
        this.company = company;
        this.name = name;
        this.sortOrder = sortOrder;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    // 직급 수정
    public void update(String name, int sortOrder, boolean enabled) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }
}
