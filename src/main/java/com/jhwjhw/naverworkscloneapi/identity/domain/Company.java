package com.jhwjhw.naverworkscloneapi.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "company")
@Getter
// 여러 사용자와 부서를 묶는 회사(테넌트) 단위.
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Company() {
        // JPA가 DB에서 조회한 값을 채울 때 사용.
    }

    public Company(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }

}
