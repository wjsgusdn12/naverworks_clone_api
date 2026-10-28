package com.jhwjhw.naverworkscloneapi.notification;

import com.jhwjhw.naverworkscloneapi.identity.domain.Membership;
import com.jhwjhw.naverworkscloneapi.identity.repository.MembershipRepository;
import com.jhwjhw.naverworkscloneapi.notification.domain.Notification;
import com.jhwjhw.naverworkscloneapi.notification.domain.NotificationCategory;
import com.jhwjhw.naverworkscloneapi.notification.repository.NotificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    // 패널에 보여 주는 보관 기간. 더 오래된 알림은 조회에서 제외.
    public static final Duration RETENTION = Duration.ofDays(30);
    private static final int TITLE_MAX = 200;
    private static final int BODY_MAX = 500;

    private final NotificationRepository notificationRepository;
    private final MembershipRepository membershipRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 다른 서비스(채팅·인증 등)가 호출하는 알림 생성. 호출한 쪽의 트랜잭션에 함께 묶임.
    @Transactional
    public void create(Long membershipId, NotificationCategory category, String title, String body, String linkPath) {
        Membership membership = membershipRepository.getReferenceById(membershipId);
        Notification notification = notificationRepository.save(
                new Notification(membership, category, cut(title, TITLE_MAX), cut(body, BODY_MAX), linkPath));
        // DB 커밋이 끝난 뒤에만 실시간으로 전달.
        eventPublisher.publishEvent(new NotificationCreatedEvent(membershipId, NotificationResponse.Item.from(notification)));
        log.debug("Notification created. membershipId={}, category={}, notificationId={}", membershipId, category,
                notification.getId());
    }

    // 최근 30일 알림을 최신순으로. category가 null이면 전체.
    @Transactional(readOnly = true)
    public List<NotificationResponse.Item> list(Long membershipId, NotificationCategory category) {
        Instant since = Instant.now().minus(RETENTION);
        List<Notification> notifications = category == null
                ? notificationRepository.findByMembershipIdAndCreatedAtAfterOrderByIdDesc(membershipId, since)
                : notificationRepository.findByMembershipIdAndCategoryAndCreatedAtAfterOrderByIdDesc(membershipId,
                        category, since);
        return notifications.stream().map(NotificationResponse.Item::from).toList();
    }

    @Transactional
    public NotificationResponse.Item read(Long membershipId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMembershipId(notificationId, membershipId)
                .orElseThrow(() -> new NotificationException("NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."));
        notification.markRead();
        return NotificationResponse.Item.from(notification);
    }

    @Transactional
    public int readAll(Long membershipId) {
        return notificationRepository.markAllRead(membershipId, Instant.now());
    }

    // 컬럼 길이를 넘는 긴 메시지는 말줄임으로 잘라 저장 실패를 막음.
    private static String cut(String value, int max) {
        String text = value == null ? "" : value.strip();
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
