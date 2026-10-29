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

    // 구성원 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // 사용자 계정
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    // 부서
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    // 직급
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_grade_id")
    private JobGrade jobGrade;

    // 직책
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    // 전화번호
    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    // 입사일
    @Column(name = "hired_at")
    private LocalDate hiredAt;

    // 상태 설정(바쁨·휴가 등). NULL이면 설정 안 함.
    @Enumerated(EnumType.STRING)
    @Column(name = "status_type", length = 30)
    private MemberStatusType statusType;

    // 상태 자동 해제 시각. NULL이면 직접 해제할 때까지 유지. (컬럼명은 예전 이름 away_until 그대로)
    @Column(name = "away_until")
    private Instant statusUntil;

    // 역할
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    // 구성원 생성 시간
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 구성원 생성
    protected Membership() {
        // JPA 전용 기본 생성자.
    }

    // 구성원 생성
    public Membership(Company company, UserAccount userAccount, Department department, Role role) {
        this.company = company;
        this.userAccount = userAccount;
        this.department = department;
        this.role = role;
        this.createdAt = Instant.now();
    }

    // 구성원 프로필 수정
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

    // 회사 옮기기. 소속·부서·직급·직책·역할을 새 회사 기준으로 한 번에 바꿈.
    public void moveTo(Company company, Department department, JobGrade jobGrade, String jobTitle, Role role) {
        this.company = company;
        this.department = department;
        this.jobGrade = jobGrade;
        this.jobTitle = jobTitle;
        this.role = role;
    }

    // 상태 설정. statusType이 null이면 해제.
    public void updateStatus(MemberStatusType statusType, Instant statusUntil) {
        this.statusType = statusType;
        this.statusUntil = statusType == null ? null : statusUntil;
    }

    // 지금 유효한 상태. 자동 해제 시각이 지났으면 null(설정 안 함).
    public MemberStatusType currentStatusType() {
        if (statusType == null) {
            return null;
        }
        if (statusUntil != null && !statusUntil.isAfter(Instant.now())) {
            return null;
        }
        return statusType;
    }

    // 지금 유효한 상태의 자동 해제 시각. 상태가 없거나 지났으면 null.
    public Instant currentStatusUntil() {
        return currentStatusType() == null ? null : statusUntil;
    }

}
