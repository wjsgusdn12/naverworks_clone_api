package com.jhwjhw.naverworkscloneapi.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;

@Entity
@Table(name = "membership")
@Getter
// 사용자와 회사의 소속 관계이며 회사별 역할과 부서 정보를 함께 보관.
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_grade_id")
    private JobGrade jobGrade;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "hired_at")
    private LocalDate hiredAt;

    @Column(name = "away_until")
    private Instant awayUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Membership() {
        // JPA 전용 기본 생성자.
    }

    public Membership(Company company, UserAccount userAccount, Department department, Role role) {
        this.company = company;
        this.userAccount = userAccount;
        this.department = department;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public void updateProfile(
            Department department,
            JobGrade jobGrade,
            String jobTitle,
            String phoneNumber,
            LocalDate hiredAt,
            Role role
    ) {
        this.department = department;
        this.jobGrade = jobGrade;
        this.jobTitle = jobTitle;
        this.phoneNumber = phoneNumber;
        this.hiredAt = hiredAt;
        this.role = role;
    }

    // 부재중은 최대 일주일까지만 허용하며 만료 시 Service에서 해제.
    public void setAwayUntil(Instant awayUntil) {
        this.awayUntil = awayUntil;
    }

}
