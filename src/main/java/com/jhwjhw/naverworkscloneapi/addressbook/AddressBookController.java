package com.jhwjhw.naverworkscloneapi.addressbook;

import com.jhwjhw.naverworkscloneapi.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/addressBook")
@RequiredArgsConstructor
// 주소록 조회·즐겨찾기·상태 설정 API를 제공.
public class AddressBookController {

    private final AddressBookService addressBookService;

    @GetMapping("/organization")
    public ApiResponse<java.util.List<AddressBookResponse.OrganizationResponse>> organization(
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("ADDRESS_BOOK_ORGANIZATION_FOUND", "조직 정보를 조회했습니다.",
                addressBookService.getOrganization(membershipId(jwt)));
    }

    @GetMapping("/member")
    public ApiResponse<AddressBookResponse.MemberPageResponse> member(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "false") boolean favoriteOnly,
            @RequestParam(defaultValue = "false") boolean allCompany) {
        return ApiResponse.success("ADDRESS_BOOK_MEMBER_FOUND", "구성원 목록을 조회했습니다.",
                addressBookService.getMembers(membershipId(jwt), keyword, companyId, departmentId, enabled, page, size,
                        sort, direction, favoriteOnly, allCompany));
    }

    @GetMapping("/member/detail")
    public ApiResponse<AddressBookResponse.MemberDetailResponse> memberDetail(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Long membershipId) {
        return ApiResponse.success("ADDRESS_BOOK_MEMBER_DETAIL_FOUND", "구성원 상세 정보를 조회했습니다.",
                addressBookService.getMemberDetail(membershipId(jwt), membershipId));
    }

    @GetMapping("/member/suggest")
    public ApiResponse<AddressBookResponse.MemberPageResponse> memberSuggest(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("ADDRESS_BOOK_MEMBER_SUGGEST_FOUND", "구성원 검색 미리보기를 조회했습니다.",
                addressBookService.getMemberSuggestions(membershipId(jwt), keyword, page, size));
    }

    @GetMapping("/searchHistory")
    public ApiResponse<java.util.List<AddressBookResponse.SearchHistoryResponse>> searchHistory(
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("ADDRESS_BOOK_SEARCH_HISTORY_FOUND", "최근 검색어를 조회했습니다.",
                addressBookService.getSearchHistory(membershipId(jwt)));
    }

    @PostMapping("/searchHistory/create")
    public ApiResponse<Void> createSearchHistory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressBookRequest.SearchHistoryCreateRequest request) {
        addressBookService.createSearchHistory(membershipId(jwt), request.keyword());
        return ApiResponse.success("ADDRESS_BOOK_SEARCH_HISTORY_CREATED", "최근 검색어를 저장했습니다.", null);
    }

    @PostMapping("/searchHistory/delete")
    public ApiResponse<Void> deleteSearchHistory(@AuthenticationPrincipal Jwt jwt) {
        addressBookService.deleteSearchHistory(membershipId(jwt));
        return ApiResponse.success("ADDRESS_BOOK_SEARCH_HISTORY_DELETED", "최근 검색어를 전체 삭제했습니다.", null);
    }

    @PostMapping("/searchHistory/delete/{searchHistoryId}")
    public ApiResponse<Void> deleteSearchHistoryItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long searchHistoryId) {
        addressBookService.deleteSearchHistoryItem(membershipId(jwt), searchHistoryId);
        return ApiResponse.success("ADDRESS_BOOK_SEARCH_HISTORY_ITEM_DELETED", "최근 검색어를 삭제했습니다.", null);
    }

    @PostMapping("/favorite/create")
    public ApiResponse<Void> createFavorite(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressBookRequest.FavoriteRequest request) {
        addressBookService.createFavorite(membershipId(jwt), request.targetMembershipId());
        return ApiResponse.success("ADDRESS_BOOK_FAVORITE_CREATED", "즐겨찾기에 추가했습니다.", null);
    }

    @PostMapping("/favorite/delete")
    public ApiResponse<Void> deleteFavorite(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressBookRequest.FavoriteRequest request) {
        addressBookService.deleteFavorite(membershipId(jwt), request.targetMembershipId());
        return ApiResponse.success("ADDRESS_BOOK_FAVORITE_DELETED", "즐겨찾기에서 해제했습니다.", null);
    }

    // 내 접속 상태·상태 설정 조회(상단바 프로필 패널)
    @GetMapping("/status/me")
    public ApiResponse<AddressBookResponse.MyStatusResponse> myStatus(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("ADDRESS_BOOK_STATUS_FETCHED", "내 상태를 조회했습니다.",
                addressBookService.getMyStatus(membershipId(jwt)));
    }

    // 상태 설정(바쁨·휴가 등) 변경. statusType이 없으면 해제.
    @PostMapping("/status/update")
    public ApiResponse<AddressBookResponse.MyStatusResponse> updateStatus(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressBookRequest.StatusRequest request) {
        return ApiResponse.success("ADDRESS_BOOK_STATUS_UPDATED", "상태를 변경했습니다.",
                addressBookService.updateStatus(membershipId(jwt), request));
    }

    @GetMapping("/group")
    public ApiResponse<java.util.List<AddressBookResponse.ContactGroupResponse>> group(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String tab) {
        return ApiResponse.success("ADDRESS_BOOK_GROUP_FOUND", "주소록 그룹을 조회했습니다.",
                addressBookService.getContactGroups(membershipId(jwt), tab));
    }

    @PostMapping("/group/create")
    public ApiResponse<AddressBookResponse.ContactGroupResponse> createGroup(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressBookRequest.ContactGroupCreateRequest request) {
        return ApiResponse.success("ADDRESS_BOOK_GROUP_CREATED", "주소록 그룹을 생성했습니다.",
                addressBookService.createContactGroup(membershipId(jwt), request));
    }

    private Long membershipId(Jwt jwt) {
        return jwt.getClaim("membershipId");
    }
}
