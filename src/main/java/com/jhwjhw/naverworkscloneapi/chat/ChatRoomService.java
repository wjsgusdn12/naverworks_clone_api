package com.jhwjhw.naverworkscloneapi.chat;

import com.jhwjhw.naverworkscloneapi.chat.domain.*;
import com.jhwjhw.naverworkscloneapi.chat.repository.*;
import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {
  private final ChatRoomRepository roomRepository;
  private final ChatRoomMemberRepository memberRepository;
  private final MembershipRepository membershipRepository;

  // 1:1 채팅방 생성
  @Transactional
  public ChatRoomResponse.Room createDirect(Long requesterId, Long targetId) {
    Membership requester = member(requesterId);
    Membership target = sameCompany(requester, targetId);
    if (requesterId.equals(targetId))
      throw new IllegalArgumentException("본인과 1:1 채팅방을 만들 수 없습니다.");
    for (ChatRoomMember candidate : memberRepository.findByMembershipIdAndLeftAtIsNull(requesterId)) {
      ChatRoom room = candidate.getChatRoom();
      if (room.getRoomType() == ChatRoomType.DIRECT && room.getEndedAt() == null
          && memberRepository.findByChatRoomIdAndLeftAtIsNull(room.getId()).stream()
              .anyMatch(it -> it.getMembership().getId().equals(targetId)))
        return room(room);
    }
    ChatRoom room = roomRepository.save(new ChatRoom(requester.getCompany(), ChatRoomType.DIRECT, null, null));
    memberRepository.save(new ChatRoomMember(room, requester));
    memberRepository.save(new ChatRoomMember(room, target));
    return room(room);
  }

  // 그룹 채팅방 생성
  @Transactional
  public ChatRoomResponse.Room createGroup(Long requesterId, ChatRoomRequest.GroupCreateRequest request) {
    Membership requester = member(requesterId);
    ChatRoom room = roomRepository
        .save(new ChatRoom(requester.getCompany(), ChatRoomType.GROUP, request.name().trim(), requester));
    Set<Long> ids = new HashSet<>(request.membershipIds());
    ids.add(requesterId);
    ids.forEach(id -> memberRepository.save(new ChatRoomMember(room, sameCompany(requester, id))));
    return room(room);
  }

  // 채팅방 목록 조회
  @Transactional(readOnly = true)
  public List<ChatRoomResponse.Room> list(Long requesterId) {
    return memberRepository.findByMembershipIdAndLeftAtIsNull(requesterId).stream().map(ChatRoomMember::getChatRoom)
        .filter(room -> room.getEndedAt() == null).map(this::room).toList();
  }

  // 채팅방 상세 조회
  @Transactional(readOnly = true)
  public ChatRoomResponse.Detail detail(Long requesterId, Long roomId) {
    active(roomId, requesterId);
    ChatRoom room = roomRepository.findById(roomId).orElseThrow();
    return new ChatRoomResponse.Detail(room(room), memberRepository.findByChatRoomIdAndLeftAtIsNull(roomId).stream()
        .map(it -> it.getMembership().getId()).toList());
  }

  // 구성원 초대
  @Transactional
  public void invite(Long requesterId, Long roomId, Long targetId) {
    ChatRoom room = owner(roomId, requesterId);
    Membership target = sameCompany(member(requesterId), targetId);
    memberRepository.findByChatRoomIdAndMembershipId(roomId, targetId).ifPresentOrElse(ChatRoomMember::rejoin,
        () -> memberRepository.save(new ChatRoomMember(room, target)));
  }

  // 구성원 내보내기
  @Transactional
  public void remove(Long requesterId, Long roomId, Long targetId) {
    ChatRoom room = owner(roomId, requesterId);
    if (requesterId.equals(targetId))
      throw new IllegalArgumentException("방장은 위임 후 나갈 수 있습니다.");
    active(roomId, targetId).leave();
    endWhenEmpty(room);
  }

  // 채팅방 나가기
  @Transactional
  public void leave(Long requesterId, Long roomId) {
    ChatRoomMember mine = active(roomId, requesterId);
    ChatRoom room = mine.getChatRoom();
    if (room.getRoomType() == ChatRoomType.GROUP && room.getOwnerMembership().getId().equals(requesterId)
        && memberRepository.findByChatRoomIdAndLeftAtIsNull(roomId).size() > 1)
      throw new IllegalArgumentException("방장은 방장을 위임한 뒤 나갈 수 있습니다.");
    mine.leave();
    endWhenEmpty(room);
  }

  // 방장 위임
  @Transactional
  public void transferOwner(Long requesterId, Long roomId, Long targetId) {
    ChatRoom room = owner(roomId, requesterId);
    room.transferOwner(active(roomId, targetId).getMembership());
  }

  // 구성원 조회
  private Membership member(Long id) {
    return membershipRepository.findById(id).orElseThrow();
  }

  // 같은 회사 구성원 조회
  private Membership sameCompany(Membership requester, Long id) {
    Membership target = member(id);
    if (!target.getCompany().getId().equals(requester.getCompany().getId()))
      throw new IllegalArgumentException("다른 회사 구성원은 초대할 수 없습니다.");
    return target;
  }

  // 활동 중인 구성원 조회
  private ChatRoomMember active(Long roomId, Long id) {
    return memberRepository.findByChatRoomIdAndMembershipId(roomId, id).filter(ChatRoomMember::isActive)
        .orElseThrow(() -> new IllegalArgumentException("채팅방 참가자가 아닙니다."));
  }

  // 방장 조회
  private ChatRoom owner(Long roomId, Long id) {
    ChatRoom room = active(roomId, id).getChatRoom();
    if (room.getRoomType() != ChatRoomType.GROUP || !room.getOwnerMembership().getId().equals(id))
      throw new IllegalArgumentException("방장만 수행할 수 있습니다.");
    return room;
  }

  // 빈 채팅방 종료
  private void endWhenEmpty(ChatRoom room) {
    if (memberRepository.findByChatRoomIdAndLeftAtIsNull(room.getId()).isEmpty())
      room.end();
  }

  // 채팅방 응답 생성
  private ChatRoomResponse.Room room(ChatRoom room) {
    return new ChatRoomResponse.Room(room.getId(), room.getRoomType().name(), room.getName(),
        room.getOwnerMembership() == null ? null : room.getOwnerMembership().getId(),
        memberRepository.findByChatRoomIdAndLeftAtIsNull(room.getId()).size(), room.getCreatedAt());
  }
}
