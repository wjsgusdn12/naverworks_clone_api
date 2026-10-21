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
@Table(name = "department")
@Getter
// 회사 내부의 부서. 현재는 소속(Membership)에서 선택적으로 연결.
public class Department {

    // 부서 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회사
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // 부서 이름
    @Column(nullable = false, length = 100)
    private String name;

    // 부서 활성 여부
    @Column(nullable = false)
    private boolean enabled;

    // 부서 생성 시간
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 부서 생성
    protected Department() {
        // JPA 전용 기본 생성자.
    }

    // 부서 생성
    public Department(Company company, String name) {
        this.company = company;
        this.name = name;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    // 부서 비활성화
    public void deactivate() {
        this.enabled = false;
    }

    // 부서 이름 변경
    public void rename(String name) {
        this.name = name;
    }
}
