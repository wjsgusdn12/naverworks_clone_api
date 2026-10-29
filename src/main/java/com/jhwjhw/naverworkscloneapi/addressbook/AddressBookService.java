package com.jhwjhw.naverworkscloneapi.addressbook;

import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.DepartmentResponse;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.MemberDetailResponse;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.MemberPageResponse;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.MemberPresenceStatus;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.MemberResponse;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse.OrganizationResponse;
import com.jhwjhw.naverworkscloneapi.identity.domain.Department;
import com.jhwjhw.naverworkscloneapi.identity.domain.AddressBookSearchHistory;
import com.jhwjhw.naverworkscloneapi.identity.domain.ContactGroup;
import com.jhwjhw.naverworkscloneapi.identity.domain.ContactGroupMember;
import com.jhwjhw.naverworkscloneapi.identity.domain.MemberFavorite;
import com.jhwjhw.naverworkscloneapi.identity.repository.ContactGroupMemberRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.ContactGroupRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.CompanyRepository;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.repository.DepartmentRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.AddressBookSearchHistoryRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MemberFavoriteRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.presence.PresenceService;
import jakarta.persistence.criteria.JoinType;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
// 회사 범위 주소록 조회와 사용자별 즐겨찾기·상태 설정을 담당.
public class AddressBookService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int SUGGESTION_PAGE_SIZE = 50;
    private static final int MAX_SEARCH_HISTORY_SIZE = 10;
    // 상태 자동 해제 시각의 상한.
    private static final Duration MAX_STATUS_DURATION = Duration.ofDays(90);

    private final MembershipRepository membershipRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final MemberFavoriteRepository memberFavoriteRepository;
    private final ContactGroupRepository contactGroupRepository;
    private final ContactGroupMemberRepository contactGroupMemberRepository;
    private final AddressBookSearchHistoryRepository addressBookSearchHistoryRepository;
    private final PresenceService presenceService;

    @Transactional(readOnly = true)
    public List<OrganizationResponse> getOrganization(Long requesterMembershipId) {
        Membership requester = findMembership(requesterMembershipId);
        // 회사 수만큼 쿼리가 늘지 않도록 부서 전체와 회사별 인원수를 각각 한 번에 읽어 Map으로 묶음.
        Map<Long, List<Department>> departmentsByCompany = departmentRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.groupingBy(department -> department.getCompany().getId()));
        Map<Long, Long> memberCountByCompany = membershipRepository.countEnabledByCompany().stream()
                .collect(Collectors.toMap(MembershipRepository.CompanyMemberCount::getCompanyId,
                        MembershipRepository.CompanyMemberCount::getMemberCount));
        return companyRepository.findAllByOrderByNameAsc().stream()
                .map(company -> new OrganizationResponse(
                        company.getId(),
                        company.getName(),
                        company.getId().equals(requester.getCompany().getId()),
                        memberCountByCompany.getOrDefault(company.getId(), 0L),
                        departmentsByCompany.getOrDefault(company.getId(), List.of()).stream()
                                .map(department -> new DepartmentResponse(department.getId(), department.getName(),
                                        department.isEnabled()))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MemberPageResponse getMembers(
            Long requesterMembershipId,
            String keyword,
            Long companyId,
            Long departmentId,
            Boolean enabled,
            int page,
            int size,
            String sort,
            String direction,
            boolean favoriteOnly,
            boolean allCompany) {
        Membership requester = findMembership(requesterMembershipId);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(normalizedPage, normalizedSize, toSort(sort, direction));

        Long targetCompanyId = companyId;
        if (companyId != null && !companyRepository.existsById(targetCompanyId)) {
            throw new AddressBookException("ADDRESS_BOOK_COMPANY_NOT_FOUND", "회사를 찾을 수 없습니다.");
        }

        // 메시지 후보처럼 전체 회사 조회가 필요한 화면만 allCompany=true를 사용.
        boolean applyCompanyFilter = companyId != null || (!favoriteOnly && !allCompany);
        if (applyCompanyFilter && targetCompanyId == null) {
            targetCompanyId = requester.getCompany().getId();
        }

        Specification<Membership> specification = Specification.where(applyCompanyFilter
                ? companyEquals(targetCompanyId)
                : Specification.unrestricted())
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

        return new MemberPageResponse(content, memberPage.getNumber(), memberPage.getSize(),
                memberPage.getTotalElements(), memberPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public MemberPageResponse getMemberSuggestions(Long requesterMembershipId, String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return new MemberPageResponse(List.of(), 0, SUGGESTION_PAGE_SIZE, 0, 0);
        }

        // 자동완성은 이름 순서로만 조회해 입력 중 결과가 흔들리지 않게 유지.
        return getMembers(
                requesterMembershipId,
                keyword,
                null,
                null,
                null,
                page,
                Math.min(Math.max(size, 1), SUGGESTION_PAGE_SIZE),
                "name",
                "asc",
                false,
                false);
    }

    @Transactional(readOnly = true)
    public List<AddressBookResponse.SearchHistoryResponse> getSearchHistory(Long requesterMembershipId) {
        return addressBookSearchHistoryRepository.findByOwnerMembershipIdOrderByUpdatedAtDesc(requesterMembershipId)
                .stream()
                .limit(MAX_SEARCH_HISTORY_SIZE)
                .map(history -> new AddressBookResponse.SearchHistoryResponse(history.getId(), history.getKeyword(),
                        history.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public void createSearchHistory(Long requesterMembershipId, String keyword) {
        Membership requester = findMembership(requesterMembershipId);
        String normalizedKeyword = keyword.trim();
        AddressBookSearchHistory history = addressBookSearchHistoryRepository
                .findByOwnerMembershipIdAndKeyword(requesterMembershipId, normalizedKeyword)
                .orElseGet(() -> new AddressBookSearchHistory(requester, normalizedKeyword));
        history.touch();
        addressBookSearchHistoryRepository.save(history);

        List<AddressBookSearchHistory> histories = addressBookSearchHistoryRepository
                .findByOwnerMembershipIdOrderByUpdatedAtDesc(requesterMembershipId);
        if (histories.size() > MAX_SEARCH_HISTORY_SIZE) {
            addressBookSearchHistoryRepository.deleteAll(histories.subList(MAX_SEARCH_HISTORY_SIZE, histories.size()));
        }
    }

    @Transactional
    public void deleteSearchHistory(Long requesterMembershipId) {
        addressBookSearchHistoryRepository.deleteByOwnerMembershipId(requesterMembershipId);
    }

    @Transactional
    public void deleteSearchHistoryItem(Long requesterMembershipId, Long searchHistoryId) {
        // 본인 검색어만 삭제해 다른 사용자의 최근 검색어를 보호.
        AddressBookSearchHistory history = addressBookSearchHistoryRepository
                .findByIdAndOwnerMembershipId(searchHistoryId, requesterMembershipId)
                .orElseThrow(
                        () -> new AddressBookException("ADDRESS_BOOK_SEARCH_HISTORY_NOT_FOUND", "최근 검색어를 찾을 수 없습니다."));
        addressBookSearchHistoryRepository.delete(history);
    }

    @Transactional(readOnly = true)
    public MemberDetailResponse getMemberDetail(Long requesterMembershipId, Long targetMembershipId) {
        findMembership(requesterMembershipId);
        // 조직도 전체 조회에서는 다른 회사 프로필도 읽기 전용으로 확인 가능.
        Membership target = findMembership(targetMembershipId);
        boolean favorite = memberFavoriteRepository
                .findByOwnerMembershipIdAndTargetMembershipId(requesterMembershipId, targetMembershipId).isPresent();
        return toMemberDetailResponse(target, requesterMembershipId, favorite);
    }

    @Transactional
    public void createFavorite(Long requesterMembershipId, Long targetMembershipId) {
        Membership requester = findMembership(requesterMembershipId);
        // 주소록 전체 조직도에서 선택한 다른 회사 구성원도 개인 즐겨찾기에 추가 가능.
        Membership target = findMembership(targetMembershipId);
        if (memberFavoriteRepository
                .findByOwnerMembershipIdAndTargetMembershipId(requesterMembershipId, targetMembershipId).isPresent()) {
            return;
        }

        memberFavoriteRepository.save(new MemberFavorite(requester, target));
        log.info("Address book favorite created. ownerMembershipId={}, targetMembershipId={}", requesterMembershipId,
                targetMembershipId);
    }

    @Transactional
    public void deleteFavorite(Long requesterMembershipId, Long targetMembershipId) {
        MemberFavorite favorite = memberFavoriteRepository
                .findByOwnerMembershipIdAndTargetMembershipId(requesterMembershipId, targetMembershipId)
                .orElseThrow(() -> new AddressBookException("ADDRESS_BOOK_FAVORITE_NOT_FOUND", "즐겨찾기 정보를 찾을 수 없습니다."));
        memberFavoriteRepository.delete(favorite);
        log.info("Address book favorite deleted. ownerMembershipId={}, targetMembershipId={}", requesterMembershipId,
                targetMembershipId);
    }

    // 내 접속 상태와 상태 설정. 상단바 프로필 패널이 씀.
    @Transactional(readOnly = true)
    public AddressBookResponse.MyStatusResponse getMyStatus(Long requesterMembershipId) {
        Membership requester = findMembership(requesterMembershipId);
        return toMyStatusResponse(requester);
    }

    // 상태 설정·해제. 바뀐 상태는 WebSocket으로 다른 구성원에게도 바로 알림.
    @Transactional
    public AddressBookResponse.MyStatusResponse updateStatus(Long requesterMembershipId,
            AddressBookRequest.StatusRequest request) {
        Membership requester = findMembership(requesterMembershipId);
        if (request.statusType() == null) {
            requester.updateStatus(null, null);
        } else {
            // 자동 해제 시각은 지금 이후여야 하고 너무 멀면(90일 초과) 거부. 없으면 직접 해제할 때까지 유지.
            Instant until = request.statusUntil();
            if (until != null) {
                Instant now = Instant.now();
                if (!until.isAfter(now) || until.isAfter(now.plus(MAX_STATUS_DURATION))) {
                    throw new AddressBookException("ADDRESS_BOOK_STATUS_UNTIL_INVALID",
                            "상태 해제 시각은 지금 이후, 90일 이내로 설정해 주세요.");
                }
            }
            requester.updateStatus(request.statusType(), until);
        }
        log.info("Address book status updated. membershipId={}, statusType={}, statusUntil={}",
                requesterMembershipId, request.statusType(), request.statusUntil());
        presenceService.publishCurrent(requesterMembershipId);
        return toMyStatusResponse(requester);
    }

    private AddressBookResponse.MyStatusResponse toMyStatusResponse(Membership requester) {
        return new AddressBookResponse.MyStatusResponse(resolvePresenceStatus(requester),
                requester.currentStatusType(), requester.currentStatusUntil());
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
    public AddressBookResponse.ContactGroupResponse createContactGroup(Long requesterMembershipId,
            AddressBookRequest.ContactGroupCreateRequest request) {
        Membership requester = findMembership(requesterMembershipId);
        ContactGroup group = contactGroupRepository
                .save(new ContactGroup(requester.getCompany(), request.name().trim(), requester));
        Set<Long> memberIds = new HashSet<>(request.membershipIds());
        memberIds.add(requesterMembershipId);
        for (Long membershipId : memberIds) {
            Membership target = findMembershipInCompany(membershipId, requester.getCompany().getId());
            contactGroupMemberRepository.save(new ContactGroupMember(group, target));
        }
        log.info("Address book group created. groupId={}, masterMembershipId={}, memberCount={}", group.getId(),
                requesterMembershipId, memberIds.size());
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
                members.stream().map(Membership::getId).toList()).stream()
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
                resolvePresenceStatus(member),
                member.currentStatusType(),
                member.currentStatusUntil(),
                favorite);
    }

    private MemberDetailResponse toMemberDetailResponse(Membership member, Long requesterMembershipId,
            boolean favorite) {
        return new MemberDetailResponse(
                member.getId(), member.getUserAccount().getDisplayName(), member.getJobTitle(),
                member.getJobGrade() == null ? null : member.getJobGrade().getName(),
                member.getDepartment() == null ? null : member.getDepartment().getName(), member.getCompany().getName(),
                member.getUserAccount().getEmail(), member.getPhoneNumber(), member.getHiredAt(),
                member.getUserAccount().isEnabled(), resolvePresenceStatus(member),
                member.currentStatusType(), member.currentStatusUntil(), favorite);
    }

    // 접속 상태. 상태 설정(바쁨·휴가 등)은 별도 필드라 여기서는 연결 여부만 봄.
    private MemberPresenceStatus resolvePresenceStatus(Membership member) {
        if (!member.getUserAccount().isEnabled()) {
            return MemberPresenceStatus.INACTIVE;
        }
        return presenceService.isOnline(member.getId()) ? MemberPresenceStatus.ONLINE
                : MemberPresenceStatus.OFFLINE;
    }

    private boolean shouldIncludeGroup(ContactGroup group, Long requesterMembershipId, String tab) {
        return switch (tab == null ? "all" : tab) {
            case "member" -> contactGroupMemberRepository.existsByContactGroupIdAndMembershipId(group.getId(),
                    requesterMembershipId);
            case "master" -> group.getMasterMembership().getId().equals(requesterMembershipId);
            default -> true;
        };
    }

    private AddressBookResponse.ContactGroupResponse toContactGroupResponse(ContactGroup group,
            Long requesterMembershipId) {
        return new AddressBookResponse.ContactGroupResponse(
                group.getId(), group.getName(), contactGroupMemberRepository.countByContactGroupId(group.getId()),
                group.getMasterMembership().getUserAccount().getDisplayName(),
                contactGroupMemberRepository.existsByContactGroupIdAndMembershipId(group.getId(),
                        requesterMembershipId),
                group.getMasterMembership().getId().equals(requesterMembershipId), group.getCreatedAt());
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
        String phonePattern = "%" + normalizePhoneKeyword(keyword) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.join("userAccount", JoinType.INNER).get("displayName")),
                        pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.join("userAccount", JoinType.INNER).get("email")),
                        pattern),
                // 연락처는 하이픈·공백을 제거해 01012345678과 010-1234-5678을 동일하게 검색.
                criteriaBuilder.like(
                        criteriaBuilder.function(
                                "replace",
                                String.class,
                                criteriaBuilder.function("replace", String.class, root.get("phoneNumber"),
                                        criteriaBuilder.literal("-"), criteriaBuilder.literal("")),
                                criteriaBuilder.literal(" "),
                                criteriaBuilder.literal("")),
                        phonePattern));
    }

    private String normalizePhoneKeyword(String keyword) {
        return keyword.replace("-", "").replace(" ", "");
    }

    private Specification<Membership> departmentEquals(Long departmentId) {
        return departmentId == null
                ? Specification.unrestricted()
                : (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("department").get("id"),
                        departmentId);
    }

    private Specification<Membership> accountEnabledEquals(Boolean enabled) {
        return enabled == null
                ? Specification.unrestricted()
                : (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.join("userAccount").get("enabled"),
                        enabled);
    }
}
