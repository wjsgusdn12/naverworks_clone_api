package com.jhwjhw.naverworkscloneapi.chat.repository;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatMessageReaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageReactionRepository extends JpaRepository<ChatMessageReaction, Long> {
    // 추가·취소의 멱등 처리를 위해 이미 눌렀는지 확인.
    Optional<ChatMessageReaction> findByChatMessageIdAndMembershipIdAndEmoji(Long chatMessageId, Long membershipId,
            String emoji);

    // 한 사람은 메시지당 이모지 하나만 남길 수 있어, 다른 이모지로 바꿀 때 기존 것을 찾기 위한 조회.
    List<ChatMessageReaction> findByChatMessageIdAndMembershipId(Long chatMessageId, Long membershipId);

    // 메시지 목록 한 페이지의 리액션을 쿼리 한 번으로 집계(N+1 방지). 먼저 눌린 이모지가 앞에 오도록 정렬.
    @Query("select reaction.chatMessage.id as messageId, reaction.emoji as emoji, count(reaction) as count, "
            + "sum(case when reaction.membership.id = :membershipId then 1 else 0 end) as mineCount "
            + "from ChatMessageReaction reaction where reaction.chatMessage.id in :messageIds "
            + "group by reaction.chatMessage.id, reaction.emoji order by min(reaction.createdAt)")
    List<ChatMessageReactionSummary> findSummaryByMessageIds(@Param("messageIds") List<Long> messageIds,
            @Param("membershipId") Long membershipId);

    // 메시지당 이모지 종류 상한 검사용.
    @Query("select count(distinct reaction.emoji) from ChatMessageReaction reaction where reaction.chatMessage.id = :messageId")
    long countDistinctEmojiByChatMessageId(@Param("messageId") Long messageId);

    // 메시지에 리액션한 사람 전체 목록(누른 순서). 프로필을 함께 가져와 인원수만큼 추가 조회되지 않게 함.
    @EntityGraph(attributePaths = { "membership", "membership.userAccount", "membership.jobGrade",
            "membership.department", "membership.company" })
    List<ChatMessageReaction> findByChatMessageIdOrderByCreatedAtAsc(Long chatMessageId);
}
