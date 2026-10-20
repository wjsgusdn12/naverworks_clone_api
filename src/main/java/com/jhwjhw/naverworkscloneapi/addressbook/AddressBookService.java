package com.jhwjhw.naverworkscloneapi.addressbook;

import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookRequest.PresenceStatus;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.DepartmentResponse;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.MemberDetailResponse;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.MemberPageResponse;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.MemberPresenceStatus;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.MemberResponse;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.OrganizationResponse;
import com.jhwjhw.naverworkscloneapi.identity.domain.Department;
import com.jhwjhw.naverworkscloneapi.identity.domain.ContactGroup;
import com.jhwjhw.naverworkscloneapi.identity.domain.ContactGroupMember;
import com.jhwjhw.naverworkscloneapi.identity.domain.MemberFavorite;
import com.jhwjhw.naverworkscloneapi.identity.repository.ContactGroupMemberRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.ContactGroupRepository;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.repository.DepartmentRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MemberFavoriteRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import jakarta.persistence.criteria.JoinType;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
// 회사 범위 주소록 조회와 사용자별 즐겨찾기·부재중 상태를 담당.
public class AddressBookService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_AWAY_HOURS = 168;

    private final MembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;
    private final MemberFavoriteRepository memberFavoriteRepository;
    private final ContactGroupRepository contactGroupRepository;
    private final ContactGroupMemberRepository contactGroupMemberRepository;

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(Long requesterMembershipId) {
        Membership requester = findMembership(requesterMembershipId);
        List<DepartmentResponse> departments = departmentRepository.findByCompanyIdOrderByNameAsc(requester.getCompany().getId())
                .stream()
                .map(department -> new DepartmentResponse(department.getId(), department.getName(), department.isEnabled()))
                .toList();
        return new OrganizationResponse(requester.getCompany().getId(), requester.getCompany().getName(), departments);
    }

    @Transactional(readOnly = true)
    public MemberPageResponse getMembers(
            Long requesterMembershipId,
            String keyword,
            Long departmentId,
            Boolean enabled,
            int page,
            int size,
            String sort,
            String direction,
            boolean favoriteOnly
    ) {
        Membership requester = findMembership(requesterMembershipId);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(normalizedPage, normalizedSize, toSort(sort, direction));

        Specification<Membership> specification = Specification.where(companyEquals(requester.getCompany().getId()))
                .and(keywordContains(keyword))
                .and(departmentEquals(departmentId))
                .and(accountEnabledEquals(enabled));

        if (favoriteOnly) {
            Set<Long> favoriteIds = favoriteIds(requesterMembershipId);
            if (favoriteIds.isEmpty()) {
                return new MemberPageResponse(List.of(), normalizedPage, normalizedSize, 0, 0);
            }
            specification = specification.and((root, query, criteriaBuilder) -> root.get("id").in(favoriteIds));
        }

        Page<Membership> memberPage = membershipRepository.findAll(specification, pageable);
        Set<Long> favoriteIds = favoriteIds(requesterMembershipId, memberPage.getContent());
        List<MemberResponse> content = memberPage.getContent().stream()
                .map(member -> toMemberResponse(member, requesterMembershipId, favoriteIds.contains(member.getId())))
                .toList();

        return new MemberPageResponse(content, memberPage.getNumber(), memberPage.getSize(), memberPage.getTotalElements(), memberPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public MemberDetailResponse getMemberDetail(Long requesterMembershipId, Long targetMembershipId) {
        Membership requester = findMembership(requesterMembershipId);
        Membership target = findMembershipInCompany(targetMembershipId, requester.getCompany().getId());
        boolean favorite = memberFavoriteRepository.findByOwnerMembershipIdAndTargetMembershipId(requesterMembershipId, targetMembershipId).isPresent();
        return toMemberDetailResponse(target, requesterMembershipId, favorite);
    }

    @Transactional
    public void createFavorite(Long requesterMembershipId, Long targetMembershipId) {
        Membership requester = findMembership(requesterMembershipId);
        Membership target = findMembershipInCompany(targetMembershipId, requester.getCompany().getId());
        if (requesterMembershipId.equals(targetMembershipId)) {
            throw new AddressBookException("ADDRESS_BOOK_FAVORITE_SELF_FORBIDDEN", "본인은 즐겨찾기에 추가할 수 없습니다.");
        }

        if (memberFavoriteRepository.findByOwnerMembershipIdAndTargetMembershipId(requesterMembershipId, targetMembershipId).isPresent()) {
            return;
        }

        memberFavoriteRepository.save(new MemberFavorite(requester, target));
        log.info("Address book favorite created. ownerMembershipId={}, targetMembershipId={}", requesterMembershipId, targetMembershipId);
    }

    @Transactional
    public void deleteFavorite(Long requesterMembershipId, Long targetMembershipId) {
        MemberFavorite favorite = memberFavoriteRepository.findByOwnerMembershipIdAndTargetMembershipId(requesterMembershipId, targetMembershipId)
                .orElseThrow(() -> new AddressBookException("ADDRESS_BOOK_FAVORITE_NOT_FOUND", "즐겨찾기 정보를 찾을 수 없습니다."));
        memberFavoriteRepository.delete(favorite);
        log.info("Address book favorite deleted. ownerMembershipId={}, targetMembershipId={}", requesterMembershipId, targetMembershipId);
    }

    @Transactional
    public void updatePresence(Long requesterMembershipId, AddressBookRequest.PresenceRequest request) {
        Membership requester = findMembership(requesterMembershipId);
        if (request.status() == PresenceStatus.ONLINE) {
            requester.setAwayUntil(null);
            return;
        }

        int awayHours = request.awayHours() == null ? 0 : request.awayHours();
        if (awayHours < 1 || awayHours > MAX_AWAY_HOURS) {
            throw new AddressBookException("ADDRESS_BOOK_AWAY_HOURS_INVALID", "부재중 시간은 1시간 이상 168시간 이하로 설정해 주세요.");
        }

        requester.setAwayUntil(Instant.now().plus(Duration.ofHours(awayHours)));
        log.info("Address book presence updated. membershipId={}, status=AWAY, awayHours={}", requesterMembershipId, awayHours);
    }

    @Transactional(readOnly = true)
    public List<AddressBookResponse.ContactGroupResponse> getContactGroups(Long requesterMembershipId, String tab) {
        Membership requester = findMembership(requesterMembershipId);
        return contactGroupRepository.findByCompanyIdOrderByCreatedAtDesc(requester.getCompany().getId()).stream()
                .filter(group -> shouldIncludeGroup(group, requesterMembershipId, tab))
                .map(group -> toContactGroupResponse(group, requesterMembershipId))
                .toList();
    }

    @Transactional
    public AddressBookResponse.ContactGroupResponse createContactGroup(Long requesterMembershipId, AddressBookRequest.ContactGroupCreateRequest request) {
        Membership requester = findMembership(requesterMembershipId);
        ContactGroup group = contactGroupRepository.save(new ContactGroup(requester.getCompany(), request.name().trim(), requester));
        Set<Long> memberIds = new HashSet<>(request.membershipIds());
        memberIds.add(requesterMembershipId);
        for (Long membershipId : memberIds) {
            Membership target = findMembershipInCompany(membershipId, requester.getCompany().getId());
            contactGroupMemberRepository.save(new ContactGroupMember(group, target));
        }
        log.info("Address book group created. groupId={}, masterMembershipId={}, memberCount={}", group.getId(), requesterMembershipId, memberIds.size());
        return toContactGroupResponse(group, requesterMembershipId);
    }

    private Membership findMembership(Long membershipId) {
        return membershipRepository.findById(membershipId)
                .orElseThrow(() -> new AddressBookException("ADDRESS_BOOK_MEMBER_NOT_FOUND", "구성원 정보를 찾을 수 없습니다."));
    }

    private Membership findMembershipInCompany(Long membershipId, Long companyId) {
        Membership membership = findMembership(membershipId);
        if (!membership.getCompany().getId().equals(companyId)) {
            throw new AddressBookException("ADDRESS_BOOK_COMPANY_ACCESS_DENIED", "다른 회사의 구성원 정보에는 접근할 수 없습니다.");
        }
        return membership;
    }

    private Set<Long> favoriteIds(Long requesterMembershipId) {
        return memberFavoriteRepository.findByOwnerMembershipId(requesterMembershipId)
                .stream()
                .map(favorite -> favorite.getTargetMembership().getId())
                .collect(java.util.stream.Collectors.toSet());
    }

    private Set<Long> favoriteIds(Long requesterMembershipId, List<Membership> members) {
        if (members.isEmpty()) {
            return Set.of();
        }
        return memberFavoriteRepository.findByOwnerMembershipIdAndTargetMembershipIdIn(
                        requesterMembershipId,
                        members.stream().map(Membership::getId).toList()
                ).stream()
                .map(favorite -> favorite.getTargetMembership().getId())
                .collect(java.util.stream.Collectors.toSet());
    }

    private MemberResponse toMemberResponse(Membership member, Long requesterMembershipId, boolean favorite) {
        return new MemberResponse(
                member.getId(),
                member.getUserAccount().getDisplayName(),
                member.getJobTitle(),
                member.getJobGrade() == null ? null : member.getJobGrade().getName(),
                member.getDepartment() == null ? null : member.getDepartment().getName(),
                member.getCompany().getName(),
                member.getUserAccount().getEmail(),
                member.getPhoneNumber(),
                member.getUserAccount().isEnabled(),
                resolvePresenceStatus(member, requesterMembershipId),
                favorite
        );
    }

    private MemberDetailResponse toMemberDetailResponse(Membership member, Long requesterMembershipId, boolean favorite) {
        return new MemberDetailResponse(
                member.getId(), member.getUserAccount().getDisplayName(), member.getJobTitle(),
                member.getJobGrade() == null ? null : member.getJobGrade().getName(),
                member.getDepartment() == null ? null : member.getDepartment().getName(), member.getCompany().getName(),
                member.getUserAccount().getEmail(), member.getPhoneNumber(), member.getHiredAt(),
                member.getUserAccount().isEnabled(), resolvePresenceStatus(member, requesterMembershipId), member.getAwayUntil(), favorite
        );
    }

    private MemberPresenceStatus resolvePresenceStatus(Membership member, Long requesterMembershipId) {
        if (!member.getUserAccount().isEnabled()) {
            return MemberPresenceStatus.INACTIVE;
        }
        if (member.getAwayUntil() != null && member.getAwayUntil().isAfter(Instant.now())) {
            return MemberPresenceStatus.AWAY;
        }
        // Redis Presence 도입 전에는 현재 로그인한 본인만 온라인으로 표시.
        return member.getId().equals(requesterMembershipId) ? MemberPresenceStatus.ONLINE : MemberPresenceStatus.OFFLINE;
    }

    private boolean shouldIncludeGroup(ContactGroup group, Long requesterMembershipId, String tab) {
        return switch (tab == null ? "all" : tab) {
            case "member" -> contactGroupMemberRepository.existsByContactGroupIdAndMembershipId(group.getId(), requesterMembershipId);
            case "master" -> group.getMasterMembership().getId().equals(requesterMembershipId);
            default -> true;
        };
    }

    private AddressBookResponse.ContactGroupResponse toContactGroupResponse(ContactGroup group, Long requesterMembershipId) {
        return new AddressBookResponse.ContactGroupResponse(
                group.getId(), group.getName(), contactGroupMemberRepository.countByContactGroupId(group.getId()),
                group.getMasterMembership().getUserAccount().getDisplayName(),
                contactGroupMemberRepository.existsByContactGroupIdAndMembershipId(group.getId(), requesterMembershipId),
                group.getMasterMembership().getId().equals(requesterMembershipId), group.getCreatedAt()
        );
    }

    private Sort toSort(String sort, String direction) {
        String property = switch (sort) {
            case "email" -> "userAccount.email";
            case "department" -> "department.name";
            case "company" -> "company.name";
            case "jobGrade" -> "jobGrade.sortOrder";
            case "phone" -> "phoneNumber";
            case "hiredAt" -> "hiredAt";
            default -> "userAccount.displayName";
        };
        return Sort.by("desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC, property)
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private Specification<Membership> companyEquals(Long companyId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("company").get("id"), companyId);
    }

    private Specification<Membership> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.join("userAccount", JoinType.INNER).get("displayName")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.join("userAccount", JoinType.INNER).get("email")), pattern)
        );
    }

    private Specification<Membership> departmentEquals(Long departmentId) {
        return departmentId == null
                ? Specification.unrestricted()
                : (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("department").get("id"), departmentId);
    }

    private Specification<Membership> accountEnabledEquals(Boolean enabled) {
        return enabled == null
                ? Specification.unrestricted()
                : (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.join("userAccount").get("enabled"), enabled);
    }
}
