package com.jhwjhw.naverworkscloneapi.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jhwjhw.naverworkscloneapi.identity.domain.Company;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.domain.Role;
import com.jhwjhw.naverworkscloneapi.identity.domain.UserAccount;
import com.jhwjhw.naverworkscloneapi.identity.repository.CompanyRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.UserAccountRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:chatGroupListTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@Transactional
// 주소록 그룹 탭: 다른 회사 사람이 만든 그룹방에 초대받아도 "내가 속한 그룹"에 나와야 함.
class ChatGroupListTests {
    @Autowired private ChatRoomService service;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository accounts;
    @Autowired private MembershipRepository memberships;

    private Membership creatorA;
    private Membership colleagueA;
    private Membership guestB;
    private Membership outsiderB;

    @BeforeEach
    void setUp() {
        Company companyA = companies.save(new Company("가상 A회사"));
        Company companyB = companies.save(new Company("가상 B회사"));
        creatorA = member(companyA, "creator-a@example.com", "A 방장");
        colleagueA = member(companyA, "colleague-a@example.com", "A 동료");
        guestB = member(companyB, "guest-b@example.com", "B 초대받음");
        outsiderB = member(companyB, "outsider-b@example.com", "B 미참여");
    }

    private Membership member(Company company, String email, String name) {
        UserAccount account = accounts.save(new UserAccount(email, "test-hash", name));
        return memberships.save(new Membership(company, account, null, Role.USER));
    }

    @Test
    void invitedMemberOfOtherCompanySeesGroupAsJoined() {
        var created = service.createGroup(creatorA.getId(),
                new ChatRoomRequest.GroupCreateRequest("회사 합동방", List.of(colleagueA.getId(), guestB.getId())));

        // B회사 초대받은 사람: 방은 A회사 소속이지만 "참여 중"으로 보임.
        assertThat(service.groupRooms(guestB.getId())).singleElement().satisfies(group -> {
            assertThat(group.id()).isEqualTo(created.id());
            assertThat(group.joined()).isTrue();
            assertThat(group.owner()).isFalse();
            assertThat(group.memberCount()).isEqualTo(3);
        });
        // 구성원 보기도 가능.
        assertThat(service.groupMembers(guestB.getId(), created.id())).hasSize(3);
    }

    @Test
    void otherCompanyGroupStaysHiddenFromNonMembers() {
        var created = service.createGroup(creatorA.getId(),
                new ChatRoomRequest.GroupCreateRequest("회사 합동방", List.of(colleagueA.getId(), guestB.getId())));

        // 참여하지 않은 B회사 사람에게는 목록에도 없고 구성원 보기도 막힘.
        assertThat(service.groupRooms(outsiderB.getId())).isEmpty();
        assertThatThrownBy(() -> service.groupMembers(outsiderB.getId(), created.id()))
                .isInstanceOf(ChatException.class).hasMessage("다른 회사의 그룹은 볼 수 없습니다.");
        // 같은 회사(A)는 지금처럼 한 번만 보임.
        assertThat(service.groupRooms(colleagueA.getId())).singleElement()
                .satisfies(group -> assertThat(group.joined()).isTrue());
    }
}
