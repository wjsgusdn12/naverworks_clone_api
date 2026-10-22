package com.jhwjhw.naverworkscloneapi.chat;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessage;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageType;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoom;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomMember;
import com.jhwjhw.naverworkscloneapi.chat.domain.ChatRoomType;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatMessageRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomMemberRepository;
import com.jhwjhw.naverworkscloneapi.chat.repository.ChatRoomRepository;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRoomService {
    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final MembershipRepository membershipRepository;

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
            ChatRoomMember targetMember = memberRepository.findByChatRoomIdAndMembershipId(room.getId(), targetId).orElse(null);
            if (targetMember != null) {
                candidate.rejoin();
                targetMember.rejoin();
                return room(room, requesterId);
            }
        }

        ChatRoom room = roomRepository.save(new ChatRoom(requester.getCompany(), ChatRoomType.DIRECT, null, null));
        memberRepository.save(new ChatRoomMember(room, requester));
        memberRepository.save(new ChatRoomMember(room, target));
        log.info("Direct chat room created. roomId={}, requesterId={}, targetId={}", room.getId(), requesterId, targetId);
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
                    ChatRoom room = roomRepository.save(new ChatRoom(requester.getCompany(), ChatRoomType.SELF, null, null));
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

        ChatRoom room = roomRepository.save(new ChatRoom(requester.getCompany(), ChatRoomType.GROUP, request.name().trim(), requester));
        for (Long memberId : memberIds) {
            memberRepository.save(new ChatRoomMember(room, activeMembership(memberId)));
        }
        systemMessage(room, requester, requester.getUserAccount().getDisplayName() + "님이 그룹 채팅방을 만들었습니다.");
        log.info("Group chat room created. roomId={}, ownerId={}, memberCount={}", room.getId(), requesterId, memberIds.size());
        return room(room, requesterId);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse.Room> list(Long requesterId) {
        activeMembership(requesterId);
        return memberRepository.findByMembershipIdAndLeftAtIsNull(requesterId).stream()
                .map(ChatRoomMember::getChatRoom)
                .filter(room -> room.getEndedAt() == null)
                .map(room -> room(room, requesterId))
                // 대화 전 새 방은 생성 시각을 사용해 목록 최상단에 배치.
                .sorted(Comparator.comparing(
                        room -> room.lastMessageAt() == null ? room.createdAt() : room.lastMessageAt(),
                        Comparator.reverseOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatRoomResponse.Detail detail(Long requesterId, Long roomId) {
        active(roomId, requesterId);
        List<ChatRoomMember> activeMembers = memberRepository.findByChatRoomIdAndLeftAtIsNull(roomId);
        return new ChatRoomResponse.Detail(room(roomById(roomId), requesterId), activeMembers.stream()
                .map(member -> member.getMembership().getId()).toList(), activeMembers.stream()
                .map(this::memberProfile).toList());
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
            systemMessage(room, mine.getMembership(), mine.getMembership().getUserAccount().getDisplayName() + "님이 채팅방을 나갔습니다.");
        }
        endWhenEmpty(room);
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
    public ChatRoomResponse.Message sendMessage(Long requesterId, Long roomId, ChatRoomRequest.MessageCreateRequest request) {
        ChatRoomMember sender = active(roomId, requesterId);
        ChatRoom room = sender.getChatRoom();
        requireOpen(room);
        ChatMessage message = messageRepository.save(new ChatMessage(room, sender.getMembership(), request.content().trim(), ChatMessageType.TEXT));
        sender.markRead(message.getId());
        log.debug("Chat message sent. roomId={}, messageId={}, senderId={}", roomId, message.getId(), requesterId);
        return message(message, requesterId);
    }

    @Transactional
    public ChatRoomResponse.MessagePage messages(Long requesterId, Long roomId, Long cursor) {
        active(roomId, requesterId);
        List<ChatMessage> descending = cursor == null
                ? messageRepository.findTop50ByChatRoomIdOrderByIdDesc(roomId)
                : messageRepository.findTop50ByChatRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor);
        Long nextCursor = descending.size() == 50 ? descending.get(descending.size() - 1).getId() : null;
        List<ChatRoomResponse.Message> result = new ArrayList<>();
        for (int index = descending.size() - 1; index >= 0; index--) {
            result.add(message(descending.get(index), requesterId));
        }
        markRead(requesterId, roomId);
        return new ChatRoomResponse.MessagePage(result, nextCursor);
    }

    @Transactional
    public void markRead(Long requesterId, Long roomId) {
        ChatRoomMember member = active(roomId, requesterId);
        List<ChatMessage> latest = messageRepository.findTop50ByChatRoomIdOrderByIdDesc(roomId);
        if (!latest.isEmpty()) {
            member.markRead(latest.getFirst().getId());
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
        return memberRepository.findByChatRoomIdAndLeftAtIsNull(roomId).stream()
                .filter(member -> !member.getMembership().getId().equals(message.getSenderMembership().getId()))
                .filter(member -> (member.getLastReadMessageId() != null && member.getLastReadMessageId() >= messageId) == read)
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

    private ChatRoomMember active(Long roomId, Long membershipId) {
        ChatRoomMember member = memberRepository.findByChatRoomIdAndMembershipId(roomId, membershipId)
                .filter(ChatRoomMember::isActive)
                .orElseThrow(() -> new ChatException("CHAT_ROOM_ACCESS_DENIED", "채팅방 참가자가 아닙니다."));
        requireOpen(member.getChatRoom());
        activeMembership(membershipId);
        return member;
    }

    private ChatRoom owner(Long roomId, Long membershipId) {
        ChatRoom room = active(roomId, membershipId).getChatRoom();
        if (room.getRoomType() != ChatRoomType.GROUP || !room.getOwnerMembership().getId().equals(membershipId)) {
            throw new ChatException("CHAT_OWNER_REQUIRED", "방장만 수행할 수 있습니다.");
        }
        return room;
    }

    private ChatRoom roomById(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ChatException("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다."));
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
        List<ChatMessage> latest = messageRepository.findTop50ByChatRoomIdOrderByIdDesc(room.getId());
        ChatMessage lastMessage = latest.isEmpty() ? null : latest.getFirst();
        ChatRoomMember requester = memberRepository.findByChatRoomIdAndMembershipId(room.getId(), requesterId).orElseThrow();
        long unreadCount = messageRepository.countUnread(room.getId(), requesterId, requester.getLastReadMessageId(), ChatMessageType.TEXT);
        ChatRoomMember directMember = room.getRoomType() == ChatRoomType.DIRECT
                ? memberRepository.findByChatRoomIdAndLeftAtIsNull(room.getId()).stream().filter(member -> !member.getMembership().getId().equals(requesterId)).findFirst().orElse(null)
                : null;
        return new ChatRoomResponse.Room(room.getId(), room.getRoomType().name(), room.getName(),
                room.getOwnerMembership() == null ? null : room.getOwnerMembership().getId(),
                memberRepository.findByChatRoomIdAndLeftAtIsNull(room.getId()).size(), room.getCreatedAt(),
                lastMessage == null ? null : lastMessage.getContent(), lastMessage == null ? null : lastMessage.getCreatedAt(), lastMessage == null ? null : lastMessage.getSenderMembership().getId(), unreadCount,
                directMember == null ? null : directMember.getMembership().getId(), directMember == null ? null : directMember.getMembership().getUserAccount().getDisplayName(),
                directMember == null || directMember.getMembership().getJobGrade() == null ? null : directMember.getMembership().getJobGrade().getName(),
                directMember == null || directMember.getMembership().getDepartment() == null ? null : directMember.getMembership().getDepartment().getName(),
                directMember == null ? null : directMember.getMembership().getCompany().getName());
    }

    private ChatRoomResponse.Message message(ChatMessage message, Long requesterId) {
        long unreadCount = message.getMessageType() == ChatMessageType.TEXT
                ? memberRepository.countUnreadMembers(message.getChatRoom().getId(), message.getSenderMembership().getId(), message.getId())
                : 0;
        // unreadCount는 아직 읽지 않은 사람 수이므로, 화면용 읽음 수를 별도로 계산.
        long readCount = message.getMessageType() == ChatMessageType.TEXT
                ? Math.max(0, memberRepository.countReadableMembers(message.getChatRoom().getId(), message.getSenderMembership().getId()) - unreadCount)
                : 0;
        return new ChatRoomResponse.Message(message.getId(), message.getSenderMembership().getId(),
                message.getSenderMembership().getUserAccount().getDisplayName(), message.getContent(), message.getMessageType().name(),
                message.getCreatedAt(), unreadCount, readCount);
    }

    private ChatRoomResponse.ReadMember readMember(ChatRoomMember member, boolean read) {
        Membership membership = member.getMembership();
        String jobGrade = membership.getJobGrade() == null ? null : membership.getJobGrade().getName();
        String departmentName = membership.getDepartment() == null ? null : membership.getDepartment().getName();
        return new ChatRoomResponse.ReadMember(membership.getId(), membership.getUserAccount().getDisplayName(), jobGrade, departmentName, read);
    }

    private ChatRoomResponse.MemberProfile memberProfile(ChatRoomMember member) {
        Membership membership = member.getMembership();
        return new ChatRoomResponse.MemberProfile(membership.getId(), membership.getUserAccount().getDisplayName(),
                membership.getJobGrade() == null ? null : membership.getJobGrade().getName(),
                membership.getDepartment() == null ? null : membership.getDepartment().getName(),
                membership.getCompany().getName());
    }
}
