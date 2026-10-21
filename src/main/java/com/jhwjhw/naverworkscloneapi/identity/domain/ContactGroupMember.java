package com.jhwjhw.naverworkscloneapi.identity.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Entity
@Table(name = "contact_group_member")
@Getter
// 주소록 그룹과 구성원의 다대다 관계를 생성 시각과 함께 보관.
public class ContactGroupMember {

    // 주소록 그룹 구성원 ID
    @EmbeddedId
    private ContactGroupMemberId id;

    // 주소록 그룹
    @MapsId("contactGroupId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_group_id", nullable = false)
    private ContactGroup contactGroup;

    // 구성원
    @MapsId("membershipId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    // 주소록 그룹 구성원 생성 시간
    private Instant createdAt;

    // 주소록 그룹 구성원 생성
    protected ContactGroupMember() {
        // JPA 전용 기본 생성자.
    }

    // 주소록 그룹 구성원 생성
    public ContactGroupMember(ContactGroup contactGroup, Membership membership) {
        this.id = new ContactGroupMemberId(contactGroup.getId(), membership.getId());
        this.contactGroup = contactGroup;
        this.membership = membership;
        this.createdAt = Instant.now();
    }

    // 주소록 그룹 구성원 ID
    @Getter
    @EqualsAndHashCode
    @Embeddable
    public static class ContactGroupMemberId implements Serializable {

        // 주소록 그룹 ID
        private Long contactGroupId;
        private Long membershipId;

        // 주소록 그룹 구성원 ID 생성
        protected ContactGroupMemberId() {
            // JPA 전용 기본 생성자.
        }

        // 주소록 그룹 구성원 ID 생성
        public ContactGroupMemberId(Long contactGroupId, Long membershipId) {
            this.contactGroupId = contactGroupId;
            this.membershipId = membershipId;
        }
    }
}
