package com.jhwjhw.naverworkscloneapi.chat;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessage;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageReaction;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageType;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatNotificationMode;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoom;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomMember;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomNotice;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomType;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageReactionRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageMentionRow;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageReactionSummary;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageReadSummary;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomMemberRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomNoticeRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomUnreadSummary;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.notification.NotificationService;
import com.jhwjhw.naverworkscloneapi.notification.domain.NotificationCategory;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRoomService {
    // 보낸 뒤 이 시간이 지나면 메시지를 수정·삭제하거나 리액션을 남길 수 없음.
    static final Duration EDIT_WINDOW = Duration.ofHours(24);

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageReactionRepository reactionRepository;
    private final ChatRoomNoticeRepository noticeRepository;
    private final MembershipRepository membershipRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;

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
            return deliver(sender, copy);
        }).toList();
    }

    // 저장·읽음 처리·알림·실시간 발행까지 메시지 한 건을 보내는 공통 흐름.
    private ChatRoomResponse.Message deliver(ChatRoomMember sender, ChatMessage message) {
        ChatRoom room = sender.getChatRoom();
        messageRepository.save(message);
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
        if (request.replyToMessageId() != null) {
            ChatMessage original = requireRoomMessage(roomId, request.replyToMessageId());
            requireNotDeleted(original);
            if (original.getMessageType() != ChatMessageType.TEXT) {
                throw new ChatException("CHAT_REPLY_NOT_ALLOWED", "시스템 안내에는 답장할 수 없습니다.");
            }
            message.replyTo(original);
        }
        message.mention(validMentionedMemberIds(roomId, requesterId, request.mentionedMembershipIds()));
        ChatRoomResponse.Message response = deliver(sender, message);
        log.debug("Chat message sent. roomId={}, messageId={}, senderId={}, replyToMessageId={}",
                roomId, message.getId(), requesterId, message.getReplyToMessageId());
        return response;
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
        return publishMessageChange("MESSAGE_UPDATED", requesterId, roomId, message);
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
        List<ChatRoomResponse.Message> result = ordered.stream()
                .map(item -> message(item, summaries.get(item.getId()), reactionMap.getOrDefault(item.getId(), List.of()),
                        mentionMap.getOrDefault(item.getId(), List.of())))
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
        return message(message, readSummary, reactions, List.copyOf(message.getMentionedMembershipIds()));
    }

    private ChatRoomResponse.Message message(ChatMessage message, ChatMessageReadSummary readSummary,
            List<ChatRoomResponse.Reaction> reactions, List<Long> mentionedMembershipIds) {
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
                message.getForwardedFromMessageId() != null);
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
