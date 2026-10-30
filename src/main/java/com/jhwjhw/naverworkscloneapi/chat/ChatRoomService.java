package com.jhwjhw.naverworkscloneapi.chat;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatAttachmentKind;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessage;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageAttachment;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageReaction;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageType;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatNotificationMode;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoom;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomMember;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomNotice;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomType;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageAttachmentRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageReactionRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageMentionRow;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageReactionSummary;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageSearchRoomCount;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageReadSummary;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomMemberRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomNoticeRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomUnreadSummary;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.repository.MemberFavoriteRepository;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.notification.NotificationService;
import com.jhwjhw.naverworkscloneapi.notification.domain.NotificationCategory;
import com.jhwjhw.naverworkscloneapi.addressbook.AddressBookResponse;
import com.jhwjhw.naverworkscloneapi.presence.PresenceService;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRoomService {
    // 보낸 뒤 이 시간이 지나면 메시지를 수정·삭제하거나 리액션을 남길 수 없음.
    static final Duration EDIT_WINDOW = Duration.ofHours(24);
    // 사진·파일 모아보기 한 페이지 크기
    static final int ATTACHMENT_PAGE_SIZE = 30;
    // 상단 검색 한 페이지 크기와 검색어 최대 길이
    static final int SEARCH_PAGE_SIZE = 30;
    static final int SEARCH_KEYWORD_MAX = 100;

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageReactionRepository reactionRepository;
    private final ChatRoomNoticeRepository noticeRepository;
    private final ChatMessageAttachmentRepository attachmentRepository;
    private final ChatAttachmentStore attachmentStore;
    private final MembershipRepository membershipRepository;
    private final MemberFavoriteRepository favoriteRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    // 주소록 그룹 구성원의 접속 상태를 아바타 배지에 싣기 위해 사용.
    private final PresenceService presenceService;

    @Transactional
    public ChatRoomResponse.Room createDirect(Long requesterId, Long targetId) {
        Membership requester = activeMembership(requesterId);
        Membership target = activeMembership(targetId);
        if (requesterId.equals(targetId)) {
            throw new ChatException("CHAT_DIRECT_SELF_NOT_ALLOWED", "본인과 1:1 채팅방을 만들 수 없습니다.");
        }

        for (ChatRoomMember candidate : memberRepository.findByMembershipId(requesterId)) {
            ChatRoom room = candidate.getChatRoom();
            if (room.getRoomType() != ChatRoomType.DIRECT || room.getEndedAt() != null) {
                continue;
            }
            ChatRoomMember targetMember = memberRepository.findByChatRoomIdAndMembershipId(room.getId(), targetId)
                    .orElse(null);
            if (targetMember != null) {
                candidate.rejoin();
                targetMember.rejoin();
                return room(room, requesterId);
            }
        }

        ChatRoom room = roomRepository.save(new ChatRoom(requester.getCompany(), ChatRoomType.DIRECT, null, null));
        memberRepository.save(new ChatRoomMember(room, requester));
        memberRepository.save(new ChatRoomMember(room, target));
        log.info("Direct chat room created. roomId={}, requesterId={}, targetId={}", room.getId(), requesterId,
                targetId);
        return room(room, requesterId);
    }

    // 개인 메모처럼 사용하는 나와의 채팅방은 멤버 한 명만 유지.
    @Transactional
    public ChatRoomResponse.Room createSelf(Long requesterId) {
        Membership requester = activeMembership(requesterId);
        return memberRepository.findByMembershipId(requesterId).stream()
                .map(ChatRoomMember::getChatRoom)
                .filter(room -> room.getRoomType() == ChatRoomType.SELF && room.getEndedAt() == null)
                .findFirst()
                .map(room -> room(room, requesterId))
                .orElseGet(() -> {
                    ChatRoom room = roomRepository
                            .save(new ChatRoom(requester.getCompany(), ChatRoomType.SELF, null, null));
                    memberRepository.save(new ChatRoomMember(room, requester));
                    return room(room, requesterId);
                });
    }

    @Transactional
    public ChatRoomResponse.Room createGroup(Long requesterId, ChatRoomRequest.GroupCreateRequest request) {
        Membership requester = activeMembership(requesterId);
        Set<Long> memberIds = new HashSet<>(request.membershipIds());
        memberIds.add(requesterId);
        if (memberIds.size() < 3) {
            throw new ChatException("CHAT_GROUP_MEMBER_MINIMUM", "그룹 채팅방은 생성자를 포함해 3명 이상이어야 합니다.");
        }

        ChatRoom room = roomRepository
                .save(new ChatRoom(requester.getCompany(), ChatRoomType.GROUP, request.name().trim(), requester));
        for (Long memberId : memberIds) {
            memberRepository.save(new ChatRoomMember(room, activeMembership(memberId)));
        }
        systemMessage(room, requester, requester.getUserAccount().getDisplayName() + "님이 그룹 채팅방을 만들었습니다.");
        log.info("Group chat room created. roomId={}, ownerId={}, memberCount={}", room.getId(), requesterId,
                memberIds.size());
        return room(room, requesterId);
    }

    // 주소록 "그룹" 탭: 내 회사의 그룹 채팅방 전체(참여 여부 무관) + 내가 참여 중인 다른 회사 그룹 채팅방.
    // 그룹방은 만든 사람의 회사에 속하므로, 다른 회사 사람이 만든 방에 초대받으면 회사 기준만으로는 빠짐.
    // 인원수·마지막 대화 시각은 방 수와 무관하게 쿼리 한 번씩.
    @Transactional(readOnly = true)
    public List<ChatRoomResponse.GroupSummary> groupRooms(Long requesterId) {
        Membership requester = activeMembership(requesterId);
        Map<Long, ChatRoom> roomById = new LinkedHashMap<>();
        roomRepository.findByCompanyIdAndRoomTypeAndEndedAtIsNullOrderByCreatedAtDesc(
                requester.getCompany().getId(), ChatRoomType.GROUP)
                .forEach(room -> roomById.put(room.getId(), room));
        memberRepository.findByMembershipIdAndLeftAtIsNull(requesterId).stream()
                .map(ChatRoomMember::getChatRoom)
                .filter(room -> room.getRoomType() == ChatRoomType.GROUP && room.getEndedAt() == null)
                .forEach(room -> roomById.putIfAbsent(room.getId(), room));
        // 최근에 만든 방이 앞.
        List<ChatRoom> rooms = roomById.values().stream()
                .sorted(Comparator.comparing(ChatRoom::getCreatedAt, Comparator.reverseOrder()))
                .toList();
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<Long, List<ChatRoomMember>> activeMembers = memberRepository.findActiveByChatRoomIdIn(roomIds).stream()
                .collect(Collectors.groupingBy(member -> member.getChatRoom().getId()));
        Map<Long, ChatMessage> lastMessages = messageRepository.findLatestByChatRoomIdIn(roomIds).stream()
                .collect(Collectors.toMap(message -> message.getChatRoom().getId(), Function.identity()));
        return rooms.stream().map(room -> {
            List<ChatRoomMember> members = activeMembers.getOrDefault(room.getId(), List.of());
            boolean joined = members.stream().anyMatch(member -> member.getMembership().getId().equals(requesterId));
            Long ownerId = room.getOwnerMembership() == null ? null : room.getOwnerMembership().getId();
            ChatMessage lastMessage = lastMessages.get(room.getId());
            return new ChatRoomResponse.GroupSummary(room.getId(), room.getName(), ownerId,
                    room.getOwnerMembership() == null ? null
                            : room.getOwnerMembership().getUserAccount().getDisplayName(),
                    members.size(), joined, requesterId.equals(ownerId), room.getCreatedAt(),
                    lastMessage == null ? null : lastMessage.getCreatedAt());
        }).toList();
    }

    // 주소록 그룹 상세: 같은 회사 구성원이면 참여하지 않은 방의 구성원도 볼 수 있고,
    // 다른 회사 방은 내가 참여 중일 때만 볼 수 있음. 프로필·즐겨찾기 모두 쿼리 한 번씩.
    @Transactional(readOnly = true)
    public List<ChatRoomResponse.GroupMember> groupMembers(Long requesterId, Long roomId) {
        Membership requester = activeMembership(requesterId);
        ChatRoom room = roomRepository.findById(roomId)
                .filter(candidate -> candidate.getRoomType() == ChatRoomType.GROUP && candidate.getEndedAt() == null)
                .orElseThrow(() -> new ChatException("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다."));
        boolean sameCompany = room.getCompany().getId().equals(requester.getCompany().getId());
        boolean joined = memberRepository.findByChatRoomIdAndMembershipId(roomId, requesterId)
                .filter(ChatRoomMember::isActive).isPresent();
        if (!sameCompany && !joined) {
            throw new ChatException("CHAT_ROOM_ACCESS_DENIED", "다른 회사의 그룹은 볼 수 없습니다.");
        }
        List<ChatRoomMember> members = memberRepository.findActiveWithProfileByChatRoomId(roomId);
        Set<Long> favoriteIds = favoriteRepository.findByOwnerMembershipIdAndTargetMembershipIdIn(requesterId,
                members.stream().map(member -> member.getMembership().getId()).toList()).stream()
                .map(favorite -> favorite.getTargetMembership().getId())
                .collect(Collectors.toSet());
        Long ownerId = room.getOwnerMembership() == null ? null : room.getOwnerMembership().getId();
        return members.stream().map(member -> {
            Membership membership = member.getMembership();
            return new ChatRoomResponse.GroupMember(membership.getId(), membership.getUserAccount().getDisplayName(),
                    membership.getJobTitle(),
                    membership.getJobGrade() == null ? null : membership.getJobGrade().getName(),
                    membership.getDepartment() == null ? null : membership.getDepartment().getName(),
                    membership.getCompany().getName(), favoriteIds.contains(membership.getId()),
                    membership.getId().equals(ownerId), presenceOf(membership),
                    membership.currentStatusType(), membership.currentStatusUntil());
        }).toList();
    }

    // 접속 상태. 주소록과 같은 기준(비활성 계정 → INACTIVE, 연결 중 → ONLINE).
    private AddressBookResponse.MemberPresenceStatus presenceOf(Membership membership) {
        if (!membership.getUserAccount().isEnabled()) {
            return AddressBookResponse.MemberPresenceStatus.INACTIVE;
        }
        return presenceService.isOnline(membership.getId()) ? AddressBookResponse.MemberPresenceStatus.ONLINE
                : AddressBookResponse.MemberPresenceStatus.OFFLINE;
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse.Room> list(Long requesterId) {
        activeMembership(requesterId);
        List<ChatRoom> rooms = memberRepository.findByMembershipIdAndLeftAtIsNullAndHiddenAtIsNull(requesterId).stream()
                .map(ChatRoomMember::getChatRoom)
                .filter(room -> room.getEndedAt() == null)
                .toList();
        return rooms(rooms, requesterId).stream()
                // 대화 전 새 방은 생성 시각을 사용해 목록 최상단에 배치.
                .sorted(Comparator.comparing(ChatRoomResponse.Room::pinned).reversed()
                        .thenComparing(ChatRoomResponse.Room::pinnedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Comparator.comparing(
                                room -> room.lastMessageAt() == null ? room.createdAt() : room.lastMessageAt(),
                                Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatRoomResponse.Detail detail(Long requesterId, Long roomId) {
        // 권한 검사 때 이미 방을 함께 조회했으므로 다시 찾지 않음.
        ChatRoom room = active(roomId, requesterId).getChatRoom();
        // 구성원 프로필(계정·직급·부서·회사)을 한 쿼리로 함께 가져와 인원수만큼 추가 조회되는 N+1을 방지.
        List<ChatRoomMember> activeMembers = memberRepository.findActiveWithProfileByChatRoomId(roomId);
        return new ChatRoomResponse.Detail(room(room, requesterId), activeMembers.stream()
                .map(member -> member.getMembership().getId()).toList(),
                activeMembers.stream()
                        .map(this::memberProfile).toList(),
                activeMembers.stream()
                        .map(member -> new ChatRoomResponse.ReadCursor(member.getMembership().getId(),
                                member.getLastReadMessageId()))
                        .toList(),
                notices(roomId));
    }

    // 방 공지 목록(최근 등록이 앞). 원문이 삭제된 공지는 제외.
    private List<ChatRoomResponse.Notice> notices(Long roomId) {
        return noticeRepository.findByChatRoomIdAndRemovedAtIsNullOrderByCreatedAtDesc(roomId).stream()
                .filter(notice -> notice.getChatMessage().getDeletedAt() == null)
                .map(notice -> new ChatRoomResponse.Notice(notice.getChatMessage().getId(),
                        notice.getChatMessage().getSenderMembership().getUserAccount().getDisplayName(),
                        notice.getChatMessage().getContent(), notice.getSetByMembership().getId(),
                        notice.getSetByMembership().getUserAccount().getDisplayName(), notice.getCreatedAt()))
                .toList();
    }

    // 공지 등록. 모든 참여자가 등록할 수 있고, 방당 여러 개를 둘 수 있음. 이미 공지인 메시지면 그대로 목록만 반환.
    @Transactional
    public List<ChatRoomResponse.Notice> updateNotice(Long requesterId, Long roomId, Long messageId) {
        ChatRoomMember requester = active(roomId, requesterId);
        ChatRoom room = requester.getChatRoom();
        ChatMessage message = requireRoomMessage(roomId, messageId);
        requireNotDeleted(message);
        if (message.getMessageType() != ChatMessageType.TEXT) {
            throw new ChatException("CHAT_NOTICE_NOT_ALLOWED", "시스템 안내는 공지로 등록할 수 없습니다.");
        }
        if (noticeRepository.findByChatRoomIdAndChatMessageIdAndRemovedAtIsNull(roomId, messageId).isPresent()) {
            return notices(roomId);
        }
        if (noticeRepository.countByChatRoomIdAndRemovedAtIsNull(roomId) >= ChatRoomNotice.MAX_PER_ROOM) {
            throw new ChatException("CHAT_NOTICE_LIMIT_EXCEEDED",
                    "공지는 방당 " + ChatRoomNotice.MAX_PER_ROOM + "개까지 등록할 수 있습니다.");
        }
        noticeRepository.save(new ChatRoomNotice(room, message, requester.getMembership()));
        systemMessage(room, requester.getMembership(),
                requester.getMembership().getUserAccount().getDisplayName() + "님이 공지를 등록했습니다.");
        List<ChatRoomResponse.Notice> notices = notices(roomId);
        eventPublisher.publishEvent(new ChatNoticeChangedEvent(roomId, notices));
        log.info("Chat notice updated. roomId={}, messageId={}, requesterId={}", roomId, messageId, requesterId);
        return notices;
    }

    // 공지 내리기(모든 참여자에게서 사라짐). 이미 없으면 목록만 돌려줌(재시도에 안전).
    @Transactional
    public List<ChatRoomResponse.Notice> removeNotice(Long requesterId, Long roomId, Long messageId) {
        ChatRoomMember requester = active(roomId, requesterId);
        ChatRoomNotice notice = noticeRepository.findByChatRoomIdAndChatMessageIdAndRemovedAtIsNull(roomId, messageId)
                .orElse(null);
        if (notice == null) return notices(roomId);
        notice.remove();
        systemMessage(requester.getChatRoom(), requester.getMembership(),
                requester.getMembership().getUserAccount().getDisplayName() + "님이 공지를 내렸습니다.");
        List<ChatRoomResponse.Notice> notices = notices(roomId);
        eventPublisher.publishEvent(new ChatNoticeChangedEvent(roomId, notices));
        log.info("Chat notice removed. roomId={}, messageId={}, requesterId={}", roomId, messageId, requesterId);
        return notices;
    }

    // 메시지 전달. 선택한 방마다 같은 내용의 새 메시지를 보냄(멘션·답장은 복사하지 않음).
    @Transactional
    public List<ChatRoomResponse.Message> forwardMessage(Long requesterId, Long roomId, Long messageId,
            List<Long> targetRoomIds) {
        active(roomId, requesterId);
        ChatMessage original = requireRoomMessage(roomId, messageId);
        requireNotDeleted(original);
        if (original.getMessageType() != ChatMessageType.TEXT) {
            throw new ChatException("CHAT_FORWARD_NOT_ALLOWED", "시스템 안내는 전달할 수 없습니다.");
        }
        // 같은 방을 두 번 고르면 한 번만 보냄.
        List<Long> distinctTargets = targetRoomIds.stream().distinct().toList();
        return distinctTargets.stream().map(targetRoomId -> {
            ChatRoomMember sender = active(targetRoomId, requesterId);
            ChatMessage copy = new ChatMessage(sender.getChatRoom(), sender.getMembership(), original.getContent(),
                    ChatMessageType.TEXT);
            copy.forwardedFrom(original);
            // 사진·파일이면 같은 파일을 가리키는 첨부를 새 메시지에도 붙임.
            ChatMessageAttachment attachment = attachmentRepository.findByChatMessageId(original.getId())
                    .map(item -> item.copyFor(copy)).orElse(null);
            return deliver(sender, copy, attachment);
        }).toList();
    }

    // 저장·읽음 처리·알림·실시간 발행까지 메시지 한 건을 보내는 공통 흐름.
    private ChatRoomResponse.Message deliver(ChatRoomMember sender, ChatMessage message) {
        return deliver(sender, message, null);
    }

    // 첨부가 있으면 메시지를 먼저 저장해 ID를 받은 뒤 첨부를 연결해 저장.
    private ChatRoomResponse.Message deliver(ChatRoomMember sender, ChatMessage message,
            ChatMessageAttachment attachment) {
        ChatRoom room = sender.getChatRoom();
        messageRepository.save(message);
        if (attachment != null) attachmentRepository.save(attachment);
        sender.markRead(message.getId());
        ChatRoomResponse.Message response = message(message, readSummaries(List.of(message)).get(message.getId()), List.of());
        notifyMessage(room, sender.getMembership(), message);
        // DB 커밋이 끝난 뒤에만 구독자에게 실시간 메시지를 전달.
        eventPublisher.publishEvent(new ChatMessageCreatedEvent(room.getId(), response));
        return response;
    }

    @Transactional
    public void invite(Long requesterId, Long roomId, Long targetId) {
        ChatRoom room = owner(roomId, requesterId);
        Membership requester = activeMembership(requesterId);
        Membership target = activeMembership(targetId);
        if (memberRepository.findByChatRoomIdAndMembershipId(roomId, targetId).isPresent()) {
            ChatRoomMember member = memberRepository.findByChatRoomIdAndMembershipId(roomId, targetId).orElseThrow();
            if (member.isActive()) {
                throw new ChatException("CHAT_MEMBER_ALREADY_JOINED", "이미 채팅방에 참여 중인 구성원입니다.");
            }
            member.rejoin();
        } else {
            memberRepository.save(new ChatRoomMember(room, target));
        }
        systemMessage(room, requester, requester.getUserAccount().getDisplayName() + "님이 "
                + target.getUserAccount().getDisplayName() + "님을 초대했습니다.");
    }

    @Transactional
    public void remove(Long requesterId, Long roomId, Long targetId) {
        ChatRoom room = owner(roomId, requesterId);
        if (requesterId.equals(targetId)) {
            throw new ChatException("CHAT_OWNER_CANNOT_REMOVE_SELF", "방장은 방장을 위임한 뒤 나갈 수 있습니다.");
        }
        Membership requester = activeMembership(requesterId);
        ChatRoomMember target = active(roomId, targetId);
        target.leave();
        systemMessage(room, requester, requester.getUserAccount().getDisplayName() + "님이 "
                + target.getMembership().getUserAccount().getDisplayName() + "님을 내보냈습니다.");
        endWhenEmpty(room);
    }

    @Transactional
    public void leave(Long requesterId, Long roomId) {
        ChatRoomMember mine = active(roomId, requesterId);
        ChatRoom room = mine.getChatRoom();
        if (room.getRoomType() == ChatRoomType.GROUP && room.getOwnerMembership().getId().equals(requesterId)
                && memberRepository.findByChatRoomIdAndLeftAtIsNull(roomId).size() > 1) {
            throw new ChatException("CHAT_OWNER_TRANSFER_REQUIRED", "방장은 방장을 위임한 뒤 나갈 수 있습니다.");
        }
        mine.leave();
        if (room.getRoomType() == ChatRoomType.GROUP) {
            systemMessage(room, mine.getMembership(),
                    mine.getMembership().getUserAccount().getDisplayName() + "님이 채팅방을 나갔습니다.");
        }
        endWhenEmpty(room);
    }

    // 회사를 옮길 때 이전 회사의 모든 방에서 나감. 방장인 그룹방은 가장 먼저 들어온 다른 구성원에게 방장을 넘김.
    @Transactional
    public void leaveAllForCompanyMove(Long membershipId) {
        for (ChatRoomMember mine : memberRepository.findByMembershipId(membershipId)) {
            if (!mine.isActive()) {
                continue;
            }
            ChatRoom room = mine.getChatRoom();
            if (room.getEndedAt() != null) {
                continue;
            }
            if (room.getRoomType() == ChatRoomType.GROUP && room.getOwnerMembership() != null
                    && room.getOwnerMembership().getId().equals(membershipId)) {
                memberRepository.findByChatRoomIdAndLeftAtIsNull(room.getId()).stream()
                        .filter(member -> !member.getMembership().getId().equals(membershipId))
                        .min(Comparator.comparing(ChatRoomMember::getId))
                        .ifPresent(next -> room.transferOwner(next.getMembership()));
            }
            mine.leave();
            if (room.getRoomType() == ChatRoomType.GROUP) {
                systemMessage(room, mine.getMembership(),
                        mine.getMembership().getUserAccount().getDisplayName() + "님이 채팅방을 나갔습니다.");
            }
            endWhenEmpty(room);
        }
        log.info("Left all rooms for company move. membershipId={}", membershipId);
    }

    @Transactional
    public void hide(Long requesterId, Long roomId) {
        active(roomId, requesterId).hide();
    }

    @Transactional
    public void togglePin(Long requesterId, Long roomId) {
        active(roomId, requesterId).togglePin();
    }

    @Transactional
    public void updateNotification(Long requesterId, Long roomId, Boolean enabled, String mode) {
        ChatRoomMember member = active(roomId, requesterId);
        // 예전 enabled 요청도 허용하고, 새 요청은 세 가지 알림 모드로 처리.
        if (mode == null || mode.isBlank()) {
            member.updateNotification(Boolean.TRUE.equals(enabled));
            return;
        }
        try {
            member.updateNotificationMode(ChatNotificationMode.valueOf(mode));
        } catch (IllegalArgumentException exception) {
            throw new ChatException("CHAT_NOTIFICATION_MODE_INVALID", "알림 설정 값이 올바르지 않습니다.");
        }
    }

    @Transactional
    public void transferOwner(Long requesterId, Long roomId, Long targetId) {
        ChatRoom room = owner(roomId, requesterId);
        ChatRoomMember target = active(roomId, targetId);
        room.transferOwner(target.getMembership());
        Membership requester = activeMembership(requesterId);
        systemMessage(room, requester, requester.getUserAccount().getDisplayName() + "님이 방장을 "
                + target.getMembership().getUserAccount().getDisplayName() + "님에게 위임했습니다.");
    }

    @Transactional
    public void rename(Long requesterId, Long roomId, ChatRoomRequest.RenameRequest request) {
        ChatRoom room = owner(roomId, requesterId);
        room.rename(request.name().trim());
        Membership requester = activeMembership(requesterId);
        systemMessage(room, requester, requester.getUserAccount().getDisplayName() + "님이 그룹명을 변경했습니다.");
    }

    @Transactional
    public ChatRoomResponse.Message sendMessage(Long requesterId, Long roomId,
            ChatRoomRequest.MessageCreateRequest request) {
        ChatRoomMember sender = active(roomId, requesterId);
        ChatRoom room = sender.getChatRoom();
        requireOpen(room);
        ChatMessage message = new ChatMessage(room, sender.getMembership(), request.content().trim(), ChatMessageType.TEXT);
        ChatMessage original = replyOriginal(roomId, request.replyToMessageId());
        if (original != null) message.replyTo(original);
        message.mention(validMentionedMemberIds(roomId, requesterId, request.mentionedMembershipIds()));
        ChatRoomResponse.Message response = deliver(sender, message);
        log.debug("Chat message sent. roomId={}, messageId={}, senderId={}, replyToMessageId={}",
                roomId, message.getId(), requesterId, message.getReplyToMessageId());
        return response;
    }

    // 답장할 원문을 찾아 검사. 답장이 아니면 null.
    private ChatMessage replyOriginal(Long roomId, Long replyToMessageId) {
        if (replyToMessageId == null) return null;
        ChatMessage original = requireRoomMessage(roomId, replyToMessageId);
        requireNotDeleted(original);
        if (original.getMessageType() != ChatMessageType.TEXT) {
            throw new ChatException("CHAT_REPLY_NOT_ALLOWED", "시스템 안내에는 답장할 수 없습니다.");
        }
        return original;
    }

    // 사진·파일 전송. 파일 하나가 메시지 하나가 됨. 본문에는 사진이면 "사진", 파일이면 파일 이름을 넣어
    // 방 목록 미리보기·알림·답장 인용·공지가 별도 처리 없이 자연스럽게 보이게 함.
    @Transactional
    public ChatRoomResponse.Message sendAttachment(Long requesterId, Long roomId, MultipartFile file,
            Long replyToMessageId) {
        ChatRoomMember sender = active(roomId, requesterId);
        ChatRoom room = sender.getChatRoom();
        requireOpen(room);
        // 권한·답장 검사를 먼저 끝내고 나서 디스크에 저장.
        ChatMessage original = replyOriginal(roomId, replyToMessageId);
        ChatAttachmentStore.StoredFile stored = attachmentStore.store(file);
        String content = stored.kind() == ChatAttachmentKind.IMAGE ? "사진" : stored.name();
        ChatMessage message = new ChatMessage(room, sender.getMembership(), content, ChatMessageType.TEXT);
        if (original != null) message.replyTo(original);
        ChatMessageAttachment attachment = new ChatMessageAttachment(message, stored.kind(), stored.name(),
                stored.contentType(), stored.size(), stored.key(), stored.width(), stored.height());
        ChatRoomResponse.Message response = deliver(sender, message, attachment);
        log.info("Chat attachment sent. roomId={}, messageId={}, senderId={}, kind={}, size={}",
                roomId, message.getId(), requesterId, stored.kind(), stored.size());
        return response;
    }

    // 방 상세의 사진·파일 모아보기(최신순). 삭제된 메시지의 첨부는 빠짐.
    @Transactional(readOnly = true)
    public ChatRoomResponse.AttachmentPage attachments(Long requesterId, Long roomId, String kind, Long cursor) {
        active(roomId, requesterId);
        ChatAttachmentKind attachmentKind;
        try {
            attachmentKind = ChatAttachmentKind.valueOf(kind);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ChatException("CHAT_ATTACHMENT_KIND_INVALID", "사진 또는 파일 중에서 골라 주세요.");
        }
        // 한 개 더 읽어 다음 페이지가 있는지 판단.
        List<ChatMessageAttachment> rows = attachmentRepository.findGallery(roomId, attachmentKind,
                cursor == null ? Long.MAX_VALUE : cursor, PageRequest.of(0, ATTACHMENT_PAGE_SIZE + 1));
        List<ChatMessageAttachment> page = rows.stream().limit(ATTACHMENT_PAGE_SIZE).toList();
        Long nextCursor = rows.size() > ATTACHMENT_PAGE_SIZE ? page.getLast().getId() : null;
        return new ChatRoomResponse.AttachmentPage(page.stream()
                .map(item -> new ChatRoomResponse.AttachmentItem(attachment(item), item.getChatMessage().getId(),
                        item.getChatMessage().getSenderMembership().getId(),
                        item.getChatMessage().getSenderMembership().getUserAccount().getDisplayName(),
                        item.getCreatedAt()))
                .toList(), nextCursor);
    }

    // 상단 검색 [메시지] 왼쪽 목록: 참여 중인 모든 방(숨긴 방 포함)을 방별 개수와 함께. 최근에 맞는 메시지가 있는 방이 위.
    @Transactional(readOnly = true)
    public List<ChatRoomResponse.MessageSearchRoomItem> searchMessageRooms(Long requesterId, String keyword) {
        Membership requester = activeMembership(requesterId);
        List<ChatMessageSearchRoomCount> counts = messageRepository.countSearchByRoom(requesterId,
                ChatMessageType.TEXT, searchPattern(keyword));
        Map<Long, ChatRoom> roomById = roomRepository
                .findAllById(counts.stream().map(ChatMessageSearchRoomCount::getRoomId).toList()).stream()
                .collect(Collectors.toMap(ChatRoom::getId, Function.identity()));
        Map<Long, ChatRoomResponse.SearchRoom> roomMap = searchRooms(requester,
                counts.stream().map(count -> roomById.get(count.getRoomId())).toList());
        return counts.stream()
                .map(count -> new ChatRoomResponse.MessageSearchRoomItem(roomMap.get(count.getRoomId()),
                        count.getMatchCount()))
                .toList();
    }

    // 상단 검색 [메시지] 오른쪽 목록: 고른 방에서 키워드가 들어간 메시지를 최신순으로 30개씩.
    @Transactional(readOnly = true)
    public ChatRoomResponse.MessageSearchPage searchMessages(Long requesterId, String keyword, Long roomId,
            Long cursor) {
        Membership requester = activeMembership(requesterId);
        String pattern = searchPattern(keyword);
        // 참여 중인 방인지 먼저 확인(아니면 "채팅방 참가자가 아닙니다").
        ChatRoom room = active(roomId, requesterId).getChatRoom();
        // 한 개 더 읽어 다음 페이지가 있는지 판단.
        List<ChatMessage> rows = messageRepository.searchByContent(requesterId, ChatMessageType.TEXT, pattern,
                roomId, cursor == null ? Long.MAX_VALUE : cursor, PageRequest.of(0, SEARCH_PAGE_SIZE + 1));
        List<ChatMessage> page = rows.stream().limit(SEARCH_PAGE_SIZE).toList();
        ChatRoomResponse.SearchRoom searchRoom = searchRooms(requester, List.of(room)).get(roomId);
        return new ChatRoomResponse.MessageSearchPage(page.stream()
                .map(message -> new ChatRoomResponse.MessageSearchItem(message.getId(), searchRoom,
                        message.getSenderMembership().getId(),
                        message.getSenderMembership().getUserAccount().getDisplayName(), message.getContent(),
                        message.getForwardedFromMessageId() != null, message.getCreatedAt()))
                .toList(), rows.size() > SEARCH_PAGE_SIZE ? page.getLast().getId() : null);
    }

    // 상단 검색 [파일]: 참여 중인 모든 방(숨긴 방 포함)에서 파일 이름에 키워드가 들어간 첨부를 최신순으로 30개씩.
    @Transactional(readOnly = true)
    public ChatRoomResponse.FileSearchPage searchFiles(Long requesterId, String keyword, Long cursor) {
        Membership requester = activeMembership(requesterId);
        String pattern = searchPattern(keyword);
        List<ChatMessageAttachment> rows = attachmentRepository.searchByName(requesterId, pattern,
                cursor == null ? Long.MAX_VALUE : cursor, PageRequest.of(0, SEARCH_PAGE_SIZE + 1));
        List<ChatMessageAttachment> page = rows.stream().limit(SEARCH_PAGE_SIZE).toList();
        Map<Long, ChatRoomResponse.SearchRoom> roomMap = searchRooms(requester,
                page.stream().map(ChatMessageAttachment::getChatRoom).toList());
        return new ChatRoomResponse.FileSearchPage(page.stream()
                .map(item -> new ChatRoomResponse.FileSearchItem(attachment(item), item.getChatMessage().getId(),
                        roomMap.get(item.getChatRoom().getId()),
                        item.getChatMessage().getSenderMembership().getUserAccount().getDisplayName(),
                        item.getCreatedAt()))
                .toList(), rows.size() > SEARCH_PAGE_SIZE ? page.getLast().getId() : null);
    }

    // 키워드를 '%키워드%' LIKE 패턴으로. 대소문자 무시, 키워드 속 %·_·!는 글자 그대로 찾도록 !로 이스케이프.
    private static String searchPattern(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            throw new ChatException("CHAT_SEARCH_KEYWORD_REQUIRED", "검색어를 입력해 주세요.");
        }
        if (trimmed.length() > SEARCH_KEYWORD_MAX) {
            throw new ChatException("CHAT_SEARCH_KEYWORD_TOO_LONG", "검색어는 " + SEARCH_KEYWORD_MAX + "자까지 입력할 수 있습니다.");
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    // 검색 결과에 붙일 방 정보. 숨긴 방은 화면 목록에 없으므로 이름까지 서버에서 만들어 줌. 쿼리 한 번.
    private Map<Long, ChatRoomResponse.SearchRoom> searchRooms(Membership requester, List<ChatRoom> rooms) {
        Map<Long, ChatRoom> roomById = new LinkedHashMap<>();
        rooms.forEach(room -> roomById.putIfAbsent(room.getId(), room));
        if (roomById.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ChatRoomMember>> membersByRoom = memberRepository
                .findActiveByChatRoomIdIn(List.copyOf(roomById.keySet())).stream()
                .collect(Collectors.groupingBy(member -> member.getChatRoom().getId()));
        Map<Long, ChatRoomResponse.SearchRoom> result = new LinkedHashMap<>();
        roomById.forEach((roomId, room) -> {
            List<ChatRoomMember> members = membersByRoom.getOrDefault(roomId, List.of());
            boolean hidden = members.stream()
                    .anyMatch(member -> member.getMembership().getId().equals(requester.getId())
                            && member.getHiddenAt() != null);
            Membership directMember = room.getRoomType() != ChatRoomType.DIRECT ? null
                    : members.stream().map(ChatRoomMember::getMembership)
                            .filter(membership -> !membership.getId().equals(requester.getId()))
                            .findFirst().orElse(null);
            String name = switch (room.getRoomType()) {
                case GROUP -> room.getName() == null || room.getName().isBlank() ? "이름 없는 그룹" : room.getName();
                case SELF -> requester.getUserAccount().getDisplayName();
                case DIRECT -> directMember == null ? "대화 상대 없음" : directMember.getUserAccount().getDisplayName();
            };
            result.put(roomId, new ChatRoomResponse.SearchRoom(roomId, room.getRoomType().name(), name, hidden,
                    members.size(), directMember == null ? null : directMember.getId()));
        });
        return result;
    }

    // 숨긴 방을 목록에 다시 보이게 함(검색 결과에서 숨긴 방을 열 때).
    @Transactional
    public void unhide(Long requesterId, Long roomId) {
        active(roomId, requesterId).unhide();
    }

    // 내려받기용 첨부와 실제 파일.
    public record AttachmentFile(ChatMessageAttachment attachment, Resource resource) {
    }

    // 그 방의 활성 참여자만, 삭제되지 않은 메시지의 첨부만 내려받을 수 있음.
    @Transactional(readOnly = true)
    public AttachmentFile attachmentFile(Long requesterId, Long attachmentId) {
        ChatMessageAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ChatException("CHAT_ATTACHMENT_NOT_FOUND", "파일을 찾을 수 없습니다."));
        active(attachment.getChatRoom().getId(), requesterId);
        Resource resource = attachment.getChatMessage().getDeletedAt() == null
                ? attachmentStore.load(attachment.getStorageKey()) : null;
        if (resource == null) {
            throw new ChatException("CHAT_ATTACHMENT_NOT_FOUND", "파일을 찾을 수 없습니다.");
        }
        return new AttachmentFile(attachment, resource);
    }

    // 보낸 사람을 뺀 활성 참여자에게 방별 알림 설정(끔·멘션만·전체)을 지켜 서비스 알림을 남김.
    private void notifyMessage(ChatRoom room, Membership sender, ChatMessage message) {
        String senderName = sender.getUserAccount().getDisplayName();
        // 그룹방은 방 이름, 1:1·나와의 채팅은 보낸 사람 이름을 제목으로 사용.
        String title = room.getRoomType() == ChatRoomType.GROUP && room.getName() != null ? room.getName() : senderName;
        String body = senderName + ": " + message.getContent();
        String linkPath = "/message?roomId=" + room.getId();
        for (ChatRoomMember member : memberRepository.findByChatRoomIdAndLeftAtIsNull(room.getId())) {
            Long memberId = member.getMembership().getId();
            if (memberId.equals(sender.getId()) || member.getNotificationMode() == ChatNotificationMode.NONE) {
                continue;
            }
            if (member.getNotificationMode() == ChatNotificationMode.MENTION
                    && !message.getMentionedMembershipIds().contains(memberId)) {
                continue;
            }
            notificationService.create(memberId, NotificationCategory.MESSAGE, title, body, linkPath);
        }
    }

    @Transactional
    public ChatRoomResponse.Message updateMessage(Long requesterId, Long roomId, Long messageId,
            ChatRoomRequest.MessageUpdateRequest request) {
        ChatMessage message = requireOwnMessage(requesterId, roomId, messageId);
        requireNotDeleted(message);
        requireWithinWindow(message, "CHAT_MESSAGE_EDIT_EXPIRED", "24시간 이내에 보낸 메시지만 수정할 수 있습니다.");
        requireMessageVersion(message, request.expectedVersion());
        if (attachmentRepository.existsByChatMessageId(messageId)) {
            throw new ChatException("CHAT_ATTACHMENT_NOT_EDITABLE", "사진·파일 메시지는 수정할 수 없습니다.");
        }
        String content = request.content().trim();
        if (content.isBlank() || content.length() > 2000) {
            throw new ChatException("CHAT_MESSAGE_CONTENT_INVALID", "메시지는 1~2,000자로 입력해 주세요.");
        }
        Set<Long> mentions = validMentionedMemberIds(roomId, requesterId, request.mentionedMembershipIds());
        // 변경이 없는 저장은 수정 시각과 버전을 불필요하게 올리지 않음.
        if (message.getContent().equals(content) && message.getMentionedMembershipIds().equals(mentions)) {
            return message(message, readSummaries(List.of(message)).get(messageId), reactions(message, requesterId));
        }
        message.editContent(content, mentions);
        ChatRoomResponse.Message updated = publishMessageChange("MESSAGE_UPDATED", requesterId, roomId, message);
        // 공지로 걸린 메시지면 공지 카드 내용도 바뀌므로 공지 목록을 다시 알림.
        if (noticeRepository.findByChatRoomIdAndChatMessageIdAndRemovedAtIsNull(roomId, messageId).isPresent()) {
            eventPublisher.publishEvent(new ChatNoticeChangedEvent(roomId, notices(roomId)));
        }
        return updated;
    }

    @Transactional
    public ChatRoomResponse.Message deleteMessage(Long requesterId, Long roomId, Long messageId, Long expectedVersion) {
        ChatMessage message = requireOwnMessage(requesterId, roomId, messageId);
        // 재시도한 삭제는 같은 결과를 반환하고, 중복 이벤트는 발행하지 않음.
        if (message.getDeletedAt() != null) return message(message, null, List.of());
        requireWithinWindow(message, "CHAT_MESSAGE_DELETE_EXPIRED", "24시간 이내에 보낸 메시지만 삭제할 수 있습니다.");
        requireMessageVersion(message, expectedVersion);
        message.deleteContent();
        // 공지로 걸린 메시지를 지우면 공지도 함께 내림.
        noticeRepository.findByChatRoomIdAndChatMessageIdAndRemovedAtIsNull(roomId, messageId).ifPresent(notice -> {
            notice.remove();
            eventPublisher.publishEvent(new ChatNoticeChangedEvent(roomId, notices(roomId)));
        });
        return publishMessageChange("MESSAGE_DELETED", requesterId, roomId, message);
    }

    private ChatRoomResponse.Message publishMessageChange(String type, Long requesterId, Long roomId, ChatMessage message) {
        // flush 후의 최종 버전을 전파해 REST·실시간 이벤트 순서가 바뀌어도 최신 내용 유지.
        messageRepository.saveAndFlush(message);
        ChatRoomResponse.Message response = message(message, readSummaries(List.of(message)).get(message.getId()),
                reactions(message, requesterId));
        eventPublisher.publishEvent(new ChatMessageChangedEvent(type, roomId, response));
        log.info("Chat message changed. type={}, roomId={}, messageId={}, requesterId={}, version={}",
                type, roomId, message.getId(), requesterId, message.getVersion());
        return response;
    }

    // 화면에서 버튼을 막아도 API로 직접 호출할 수 있으므로 서버에서도 24시간 제한을 검사.
    private void requireWithinWindow(ChatMessage message, String code, String reason) {
        if (message.getCreatedAt().isBefore(Instant.now().minus(EDIT_WINDOW))) {
            throw new ChatException(code, reason);
        }
    }

    private ChatMessage requireOwnMessage(Long requesterId, Long roomId, Long messageId) {
        active(roomId, requesterId);
        ChatMessage message = requireRoomMessage(roomId, messageId);
        if (!message.getSenderMembership().getId().equals(requesterId)) {
            throw new ChatException("CHAT_MESSAGE_AUTHOR_REQUIRED", "본인이 보낸 메시지만 수정·삭제할 수 있습니다.");
        }
        if (message.getMessageType() != ChatMessageType.TEXT) {
            throw new ChatException("CHAT_SYSTEM_MESSAGE_IMMUTABLE", "시스템 안내는 수정·삭제할 수 없습니다.");
        }
        return message;
    }

    private void requireNotDeleted(ChatMessage message) {
        if (message.getDeletedAt() != null) {
            throw new ChatException("CHAT_MESSAGE_ALREADY_DELETED", "이미 삭제된 메시지입니다.");
        }
    }

    private void requireMessageVersion(ChatMessage message, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion != message.getVersion()) {
            throw new ChatException("CHAT_MESSAGE_CONFLICT", "다른 화면에서 메시지가 변경됐습니다. 최신 내용을 확인해 주세요.");
        }
    }

    @Transactional
    public ChatRoomResponse.MessagePage messages(Long requesterId, Long roomId, Long cursor,
            Long anchorMessageId, Long afterCursor) {
        ChatRoomMember requesterMember = active(roomId, requesterId);
        int cursorCount = (cursor == null ? 0 : 1) + (anchorMessageId == null ? 0 : 1)
                + (afterCursor == null ? 0 : 1);
        if (cursorCount > 1 || (cursor != null && cursor <= 0)
                || (anchorMessageId != null && anchorMessageId <= 0) || (afterCursor != null && afterCursor <= 0)) {
            throw new ChatException("CHAT_MESSAGE_CURSOR_INVALID", "메시지 조회 위치가 올바르지 않습니다.");
        }
        // 목록 응답의 읽음 수가 모달 조회 결과와 같도록 먼저 읽음 위치를 갱신(권한 검사는 위에서 끝났으므로 재조회 없이 처리).
        markRead(requesterMember);
        List<ChatMessage> page;
        if (anchorMessageId != null) {
            requireRoomMessage(roomId, anchorMessageId);
            page = messageRepository.findTop50ByChatRoomIdAndIdLessThanEqualOrderByIdDesc(roomId, anchorMessageId);
        } else if (afterCursor != null) {
            page = messageRepository.findTop50ByChatRoomIdAndIdGreaterThanOrderByIdAsc(roomId, afterCursor);
        } else if (cursor != null) {
            page = messageRepository.findTop50ByChatRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor);
        } else {
            page = messageRepository.findTop50ByChatRoomIdOrderByIdDesc(roomId);
        }
        List<ChatMessage> ordered = page.stream().sorted(Comparator.comparing(ChatMessage::getId)).toList();
        Long nextCursor = afterCursor == null && page.size() == 50 ? ordered.getFirst().getId() : null;
        Long lastId = ordered.isEmpty() ? null : ordered.getLast().getId();
        Long newerCursor = lastId != null && messageRepository.existsByChatRoomIdAndIdGreaterThan(roomId, lastId)
                ? lastId : null;
        Map<Long, ChatMessageReadSummary> summaries = readSummaries(ordered);
        Map<Long, List<ChatRoomResponse.Reaction>> reactionMap = reactionSummaries(ordered, requesterId);
        Map<Long, List<Long>> mentionMap = mentions(ordered);
        Map<Long, ChatMessageAttachment> attachmentMap = ordered.isEmpty() ? Map.of()
                : attachmentRepository.findByChatMessageIdIn(ordered.stream().map(ChatMessage::getId).toList()).stream()
                        .collect(Collectors.toMap(item -> item.getChatMessage().getId(), Function.identity()));
        List<ChatRoomResponse.Message> result = ordered.stream()
                .map(item -> message(item, summaries.get(item.getId()), reactionMap.getOrDefault(item.getId(), List.of()),
                        mentionMap.getOrDefault(item.getId(), List.of()), attachmentMap.get(item.getId())))
                .toList();
        return new ChatRoomResponse.MessagePage(result, nextCursor, newerCursor);
    }

    // 원문 ID를 바꿔 다른 방의 메시지를 인용하거나 열람하는 요청 차단.
    private ChatMessage requireRoomMessage(Long roomId, Long messageId) {
        return messageRepository.findById(messageId)
                .filter(item -> item.getChatRoom().getId().equals(roomId))
                .orElseThrow(() -> new ChatException("CHAT_MESSAGE_NOT_FOUND", "원문 메시지를 찾을 수 없습니다."));
    }

    @Transactional
    public void markRead(Long requesterId, Long roomId) {
        markRead(active(roomId, requesterId));
    }

    // 최신 메시지 ID 하나만 조회해 읽음 위치를 옮김. 50건을 내려받던 이전 방식보다 전송량과 시간이 훨씬 적음.
    private void markRead(ChatRoomMember member) {
        Long roomId = member.getChatRoom().getId();
        Long latestId = messageRepository.findMaxIdByChatRoomId(roomId);
        if (latestId != null && member.markRead(latestId)) {
            // 실제 읽음 위치가 전진한 경우에만 실시간 이벤트를 발행.
            eventPublisher.publishEvent(new ChatMessageReadUpdatedEvent(roomId, member.getMembership().getId(), latestId));
        }
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse.ReadMember> readMembers(Long requesterId, Long roomId, Long messageId, boolean read) {
        active(roomId, requesterId);
        ChatMessage message = messageRepository.findById(messageId)
                .filter(item -> item.getChatRoom().getId().equals(roomId))
                .orElseThrow(() -> new ChatException("CHAT_MESSAGE_NOT_FOUND", "메시지를 찾을 수 없습니다."));
        if (message.getMessageType() != ChatMessageType.TEXT) {
            throw new ChatException("CHAT_SYSTEM_READ_UNAVAILABLE", "시스템 메시지는 읽음 현황을 제공하지 않습니다.");
        }
        requireNotDeleted(message);
        Long senderId = message.getSenderMembership().getId();
        // 프로필 연관 엔티티를 미리 함께 조회해 구성원 수와 관계없이 쿼리 수를 일정하게 유지.
        return memberRepository.findActiveWithProfileByChatRoomId(roomId).stream()
                .filter(member -> !member.getMembership().getId().equals(senderId))
                .filter(member -> (member.getLastReadMessageId() != null
                        && member.getLastReadMessageId() >= messageId) == read)
                .map(member -> readMember(member, read))
                .toList();
    }

    private Membership activeMembership(Long membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ChatException("CHAT_MEMBER_NOT_FOUND", "구성원을 찾을 수 없습니다."));
        if (!membership.getUserAccount().isEnabled()) {
            throw new ChatException("CHAT_MEMBER_INACTIVE", "비활성 구성원은 채팅을 사용할 수 없습니다.");
        }
        return membership;
    }

    // 방 참여·방 종료·계정 활성 여부를 한 번의 조회로 검사(이전에는 지연 로딩 때문에 쿼리 4회).
    private ChatRoomMember active(Long roomId, Long membershipId) {
        ChatRoomMember member = memberRepository.findWithRoomAndAccountByChatRoomIdAndMembershipId(roomId, membershipId)
                .filter(ChatRoomMember::isActive)
                .orElseThrow(() -> new ChatException("CHAT_ROOM_ACCESS_DENIED", "채팅방 참가자가 아닙니다."));
        requireOpen(member.getChatRoom());
        if (!member.getMembership().getUserAccount().isEnabled()) {
            throw new ChatException("CHAT_MEMBER_INACTIVE", "비활성 구성원은 채팅을 사용할 수 없습니다.");
        }
        return member;
    }

    private ChatRoom owner(Long roomId, Long membershipId) {
        ChatRoom room = active(roomId, membershipId).getChatRoom();
        if (room.getRoomType() != ChatRoomType.GROUP || !room.getOwnerMembership().getId().equals(membershipId)) {
            throw new ChatException("CHAT_OWNER_REQUIRED", "방장만 수행할 수 있습니다.");
        }
        return room;
    }

    private void requireOpen(ChatRoom room) {
        if (room.getEndedAt() != null) {
            throw new ChatException("CHAT_ROOM_ENDED", "종료된 채팅방입니다.");
        }
    }

    private void endWhenEmpty(ChatRoom room) {
        if (memberRepository.findByChatRoomIdAndLeftAtIsNull(room.getId()).isEmpty()) {
            room.end();
            log.info("Chat room ended because no active member remains. roomId={}", room.getId());
        }
    }

    private void systemMessage(ChatRoom room, Membership actor, String content) {
        messageRepository.save(new ChatMessage(room, actor, content, ChatMessageType.SYSTEM));
    }

    private ChatRoomResponse.Room room(ChatRoom room, Long requesterId) {
        return rooms(List.of(room), requesterId).getFirst();
    }

    // 목록에 필요한 마지막 메시지·참가자·읽음 정보를 방 단위가 아닌 묶음으로 조회.
    private List<ChatRoomResponse.Room> rooms(List<ChatRoom> rooms, Long requesterId) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<Long, ChatMessage> lastMessages = messageRepository.findLatestByChatRoomIdIn(roomIds).stream()
                .collect(Collectors.toMap(message -> message.getChatRoom().getId(), Function.identity()));
        Map<Long, List<ChatRoomMember>> activeMembers = memberRepository.findActiveByChatRoomIdIn(roomIds).stream()
                .collect(Collectors.groupingBy(member -> member.getChatRoom().getId()));
        Map<Long, ChatRoomUnreadSummary> unreadSummaries = messageRepository
                .findUnreadSummaryByChatRoomIds(roomIds, requesterId, ChatMessageType.TEXT).stream()
                .collect(Collectors.toMap(ChatRoomUnreadSummary::getRoomId, Function.identity()));
        return rooms.stream().map(room -> room(room, requesterId, lastMessages.get(room.getId()),
                activeMembers.getOrDefault(room.getId(), List.of()), unreadSummaries.get(room.getId()))).toList();
    }

    private ChatRoomResponse.Room room(ChatRoom room, Long requesterId, ChatMessage lastMessage,
            List<ChatRoomMember> activeMembers, ChatRoomUnreadSummary unreadSummary) {
        long unreadCount = unreadSummary == null ? 0 : unreadSummary.getUnreadCount();
        Instant oldestUnreadMessageAt = unreadSummary == null ? null : unreadSummary.getOldestUnreadMessageAt();
        ChatRoomMember directMember = room.getRoomType() == ChatRoomType.DIRECT
                ? activeMembers.stream().filter(member -> !member.getMembership().getId().equals(requesterId))
                        .findFirst().orElse(null)
                : null;
        ChatRoomMember requesterMember = activeMembers.stream()
                .filter(member -> member.getMembership().getId().equals(requesterId)).findFirst().orElse(null);
        return new ChatRoomResponse.Room(room.getId(), room.getRoomType().name(), room.getName(),
                room.getOwnerMembership() == null ? null : room.getOwnerMembership().getId(),
                activeMembers.size(), room.getCreatedAt(),
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                lastMessage == null ? null : lastMessage.getSenderMembership().getId(), unreadCount,
                oldestUnreadMessageAt,
                directMember == null ? null : directMember.getMembership().getId(),
                directMember == null ? null : directMember.getMembership().getUserAccount().getDisplayName(),
                directMember == null || directMember.getMembership().getJobGrade() == null ? null
                        : directMember.getMembership().getJobGrade().getName(),
                directMember == null || directMember.getMembership().getDepartment() == null ? null
                        : directMember.getMembership().getDepartment().getName(),
                directMember == null ? null : directMember.getMembership().getCompany().getName(),
                requesterMember != null && requesterMember.getPinnedAt() != null,
                requesterMember == null ? null : requesterMember.getPinnedAt(),
                requesterMember == null || requesterMember.isNotificationEnabled(),
                requesterMember == null ? ChatNotificationMode.ALL.name()
                        : requesterMember.getNotificationMode().name(),
                lastMessage != null && lastMessage.getMentionedMembershipIds().contains(requesterId),
                lastMessage == null ? null : lastMessage.getId(), lastMessage == null ? 0 : lastMessage.getVersion());
    }

    private Map<Long, ChatMessageReadSummary> readSummaries(List<ChatMessage> messages) {
        List<Long> messageIds = messages.stream().filter(message -> message.getMessageType() == ChatMessageType.TEXT)
                .map(ChatMessage::getId).toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.findReadSummaryByMessageIds(messageIds, ChatMessageType.TEXT).stream()
                .collect(Collectors.toMap(ChatMessageReadSummary::getMessageId, Function.identity()));
    }

    // 리액션 추가. 같은 이모지를 이미 눌렀으면 아무것도 바꾸지 않고 현재 집계만 반환(재시도에 안전).
    // 한 사람은 메시지당 이모지 하나만 남길 수 있으므로, 다른 이모지가 있으면 그것을 취소하고 새 것으로 바꿈.
    @Transactional
    public List<ChatRoomResponse.Reaction> addReaction(Long requesterId, Long roomId, Long messageId, String emoji) {
        ChatRoomMember requester = active(roomId, requesterId);
        ChatMessage message = requireReactableMessage(roomId, messageId, emoji);
        if (reactionRepository.findByChatMessageIdAndMembershipIdAndEmoji(messageId, requesterId, emoji).isEmpty()) {
            for (ChatMessageReaction previous : reactionRepository.findByChatMessageIdAndMembershipId(messageId, requesterId)) {
                reactionRepository.delete(previous);
                eventPublisher.publishEvent(new ChatReactionChangedEvent(roomId, messageId, previous.getEmoji(),
                        requesterId, ChatReactionChangedEvent.REMOVED));
            }
            reactionRepository.flush();
            // 새 이모지 종류가 추가되는 경우에만 상한을 검사(이미 있는 이모지에 한 명 더 누르는 건 허용).
            boolean newEmoji = reactionRepository.findSummaryByMessageIds(List.of(messageId), requesterId).stream()
                    .noneMatch(summary -> summary.getEmoji().equals(emoji));
            if (newEmoji && reactionRepository.countDistinctEmojiByChatMessageId(messageId) >= ChatMessageReaction.MAX_DISTINCT_EMOJIS) {
                throw new ChatException("CHAT_REACTION_LIMIT",
                        "한 메시지에는 최대 " + ChatMessageReaction.MAX_DISTINCT_EMOJIS + "종류의 이모지만 남길 수 있습니다.");
            }
            reactionRepository.save(new ChatMessageReaction(message, requester.getMembership(), emoji));
            eventPublisher.publishEvent(new ChatReactionChangedEvent(roomId, messageId, emoji, requesterId,
                    ChatReactionChangedEvent.ADDED));
            log.debug("Chat reaction added. roomId={}, messageId={}, emoji={}, membershipId={}", roomId, messageId, emoji,
                    requesterId);
        }
        return reactions(message, requesterId);
    }

    // 리액션 취소. 누른 적이 없으면 아무것도 바꾸지 않고 현재 집계만 반환(재시도에 안전).
    @Transactional
    public List<ChatRoomResponse.Reaction> removeReaction(Long requesterId, Long roomId, Long messageId, String emoji) {
        active(roomId, requesterId);
        ChatMessage message = requireReactableMessage(roomId, messageId, emoji);
        reactionRepository.findByChatMessageIdAndMembershipIdAndEmoji(messageId, requesterId, emoji).ifPresent(reaction -> {
            // 리액션은 취소하면 다시 보여 줄 일이 없어 행을 바로 지움(메시지와 달리 논리 삭제 불필요).
            reactionRepository.delete(reaction);
            eventPublisher.publishEvent(new ChatReactionChangedEvent(roomId, messageId, emoji, requesterId,
                    ChatReactionChangedEvent.REMOVED));
            log.debug("Chat reaction removed. roomId={}, messageId={}, emoji={}, membershipId={}", roomId, messageId,
                    emoji, requesterId);
        });
        reactionRepository.flush();
        return reactions(message, requesterId);
    }

    // 메시지에 리액션한 사람 전체 목록(누른 순서). 누가 어떤 이모지를 골랐는지 함께 내려 줌.
    @Transactional(readOnly = true)
    public List<ChatRoomResponse.ReactionMember> reactionMembers(Long requesterId, Long roomId, Long messageId) {
        active(roomId, requesterId);
        requireRoomMessage(roomId, messageId);
        return reactionRepository.findByChatMessageIdOrderByCreatedAtAsc(messageId).stream()
                .map(reaction -> {
                    Membership membership = reaction.getMembership();
                    return new ChatRoomResponse.ReactionMember(reaction.getEmoji(),
                            new ChatRoomResponse.MemberProfile(membership.getId(),
                                    membership.getUserAccount().getDisplayName(),
                                    membership.getJobGrade() == null ? null : membership.getJobGrade().getName(),
                                    membership.getDepartment() == null ? null : membership.getDepartment().getName(),
                                    membership.getCompany().getName()));
                })
                .toList();
    }

    // 리액션을 남길 수 있는 메시지인지: 이 방의 일반 텍스트이고 삭제되지 않았으며 24시간이 지나지 않았고, 이모지가 허용 목록에 있어야 함.
    private ChatMessage requireReactableMessage(Long roomId, Long messageId, String emoji) {
        if (!ChatMessageReaction.ALLOWED_EMOJIS.contains(emoji)) {
            throw new ChatException("CHAT_REACTION_INVALID", "지원하지 않는 이모지입니다.");
        }
        ChatMessage message = requireRoomMessage(roomId, messageId);
        if (message.getMessageType() != ChatMessageType.TEXT) {
            throw new ChatException("CHAT_REACTION_NOT_ALLOWED", "시스템 안내에는 리액션을 남길 수 없습니다.");
        }
        requireNotDeleted(message);
        requireWithinWindow(message, "CHAT_REACTION_EXPIRED", "24시간 이내에 보낸 메시지에만 리액션을 남길 수 있습니다.");
        return message;
    }

    // 메시지 한 건의 리액션 집계.
    private List<ChatRoomResponse.Reaction> reactions(ChatMessage message, Long requesterId) {
        return reactionSummaries(List.of(message), requesterId).getOrDefault(message.getId(), List.of());
    }

    // 메시지 묶음의 리액션을 쿼리 한 번으로 집계해 메시지 ID별로 정리.
    private Map<Long, List<ChatRoomResponse.Reaction>> reactionSummaries(List<ChatMessage> messages, Long requesterId) {
        List<Long> messageIds = messages.stream().filter(message -> message.getMessageType() == ChatMessageType.TEXT)
                .map(ChatMessage::getId).toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return reactionRepository.findSummaryByMessageIds(messageIds, requesterId).stream()
                .collect(Collectors.groupingBy(ChatMessageReactionSummary::getMessageId, LinkedHashMap::new,
                        Collectors.mapping(summary -> new ChatRoomResponse.Reaction(summary.getEmoji(),
                                summary.getCount(), summary.getMineCount() > 0), Collectors.toList())));
    }

    // 페이지 메시지들의 멘션 대상을 한 번에 읽어 메시지 ID별로 묶음. 반복문 안에서는 이 Map만 읽음.
    private Map<Long, List<Long>> mentions(List<ChatMessage> messages) {
        List<Long> messageIds = messages.stream().filter(message -> message.getMessageType() == ChatMessageType.TEXT)
                .map(ChatMessage::getId).toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.findMentionsByMessageIds(messageIds).stream()
                .collect(Collectors.groupingBy(ChatMessageMentionRow::getMessageId,
                        Collectors.mapping(ChatMessageMentionRow::getMembershipId, Collectors.toList())));
    }

    // 메시지 한 건만 다룰 때는 지연 로딩 한 번이 전부라 getter를 그대로 사용.
    private ChatRoomResponse.Message message(ChatMessage message, ChatMessageReadSummary readSummary,
            List<ChatRoomResponse.Reaction> reactions) {
        return message(message, readSummary, reactions, List.copyOf(message.getMentionedMembershipIds()),
                attachmentRepository.findByChatMessageId(message.getId()).orElse(null));
    }

    private ChatRoomResponse.Message message(ChatMessage message, ChatMessageReadSummary readSummary,
            List<ChatRoomResponse.Reaction> reactions, List<Long> mentionedMembershipIds,
            ChatMessageAttachment attachment) {
        long recipientCount = readSummary == null ? 0 : readSummary.getRecipientCount();
        long readCount = readSummary == null ? 0 : readSummary.getReadCount();
        long unreadCount = Math.max(0, recipientCount - readCount);
        boolean deleted = message.getDeletedAt() != null;
        ChatRoomResponse.Reply reply = null;
        if (!deleted && message.getReplyToMessageId() != null) {
            boolean originalDeleted = message.getReplyToMessage() == null || message.getReplyToMessage().getDeletedAt() != null;
            reply = new ChatRoomResponse.Reply(message.getReplyToMessageId(), message.getReplyToSenderName(),
                    originalDeleted ? ChatMessage.DELETED_CONTENT : message.getReplyToContent(), originalDeleted);
        }
        return new ChatRoomResponse.Message(message.getId(), message.getSenderMembership().getId(),
                message.getSenderMembership().getUserAccount().getDisplayName(), message.getContent(),
                message.getMessageType().name(),
                message.getCreatedAt(), unreadCount, readCount, mentionedMembershipIds,
                reply, message.getEditedAt(), message.getDeletedAt(), message.getVersion(), reactions,
                message.getForwardedFromMessageId() != null,
                // 삭제된 메시지는 첨부도 감춤.
                deleted || attachment == null ? null : attachment(attachment));
    }

    private ChatRoomResponse.Attachment attachment(ChatMessageAttachment attachment) {
        return new ChatRoomResponse.Attachment(attachment.getId(), attachment.getKind().name(),
                attachment.getOriginalName(), attachment.getContentType(), attachment.getSize(), attachment.getWidth(),
                attachment.getHeight());
    }

    // 현재 방의 활성 구성원만 멘션할 수 있어 다른 회사/탈퇴 구성원 ID 주입을 차단.
    private Set<Long> validMentionedMemberIds(Long roomId, Long senderId, List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> activeMemberIds = memberRepository.findByChatRoomIdAndLeftAtIsNull(roomId).stream()
                .map(member -> member.getMembership().getId())
                .collect(Collectors.toSet());
        return requestedIds.stream()
                .filter(activeMemberIds::contains)
                .filter(memberId -> !memberId.equals(senderId))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private ChatRoomResponse.ReadMember readMember(ChatRoomMember member, boolean read) {
        Membership membership = member.getMembership();
        String jobGrade = membership.getJobGrade() == null ? null : membership.getJobGrade().getName();
        String departmentName = membership.getDepartment() == null ? null : membership.getDepartment().getName();
        return new ChatRoomResponse.ReadMember(membership.getId(), membership.getUserAccount().getDisplayName(),
                jobGrade, departmentName, membership.getCompany().getName(), read);
    }

    private ChatRoomResponse.MemberProfile memberProfile(ChatRoomMember member) {
        Membership membership = member.getMembership();
        return new ChatRoomResponse.MemberProfile(membership.getId(), membership.getUserAccount().getDisplayName(),
                membership.getJobGrade() == null ? null : membership.getJobGrade().getName(),
                membership.getDepartment() == null ? null : membership.getDepartment().getName(),
                membership.getCompany().getName());
    }
}
