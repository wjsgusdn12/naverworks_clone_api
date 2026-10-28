package com.jhwjhw.naverworkscloneapi.notification.repository;

import com.jhwjhw.naverworkscloneapi.notification.domain.Notification;
import com.jhwjhw.naverworkscloneapi.notification.domain.NotificationCategory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // 기준 시각 이후의 내 알림을 최신순으로 조회(전체 칩).
    List<Notification> findByMembershipIdAndCreatedAtAfterOrderByIdDesc(Long membershipId, Instant since);

    // 분류 칩을 고른 경우의 조회.
    List<Notification> findByMembershipIdAndCategoryAndCreatedAtAfterOrderByIdDesc(Long membershipId,
            NotificationCategory category, Instant since);

    // 다른 사람의 알림을 읽음 처리하지 못하도록 소유자 조건을 함께 검사.
    Optional<Notification> findByIdAndMembershipId(Long id, Long membershipId);

    // "모두 읽음"은 행을 하나씩 바꾸지 않고 한 번의 UPDATE로 처리.
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.membership.id = :membershipId and n.readAt is null")
    int markAllRead(@Param("membershipId") Long membershipId, @Param("now") Instant now);
}
