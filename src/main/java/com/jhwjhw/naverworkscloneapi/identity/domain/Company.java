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

    // 회사 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회사 이름
    @Column(nullable = false, length = 100, unique = true)
    private String name;

    // 체험 회사 여부. 공개 가입자가 기본으로 들어가는 회사 하나만 true.
    @Column(nullable = false)
    private boolean demo;

    // 초대 코드. 관리자가 처음 볼 때 발급하므로 비어 있을 수 있음.
    @Column(name = "invite_code", length = 20, unique = true)
    private String inviteCode;

    // 회사 생성 시간
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 회사 생성
    protected Company() {
        // JPA가 DB에서 조회한 값을 채울 때 사용.
    }

    // 회사 생성
    public Company(String name) {
        this.name = name;
        this.demo = false;
        this.createdAt = Instant.now();
    }

    // 체험 회사로 지정(운영은 마이그레이션이, 테스트는 코드가 호출).
    public void markDemo() {
        this.demo = true;
    }

    // 초대 코드 발급·교체
    public void issueInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

}
