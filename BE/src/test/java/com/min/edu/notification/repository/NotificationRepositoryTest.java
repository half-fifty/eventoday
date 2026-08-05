package com.min.edu.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.notification.domain.Notification;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findByMemberIdOrderByCreatedAtDesc_returnsOnlyMemberNotificationsInLatestOrder() {
        Long memberId = insertMember();
        Long otherMemberId = insertMember();
        OffsetDateTime baseTime = OffsetDateTime.parse("2026-08-05T10:00:00+09:00");
        Long olderId = insertNotification(memberId, "이전 알림", baseTime, null);
        Long latestId = insertNotification(memberId, "최신 알림", baseTime.plusMinutes(1), null);
        insertNotification(otherMemberId, "다른 회원 알림", baseTime.plusMinutes(2), null);

        Page<Notification> result = notificationRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(Notification::getId)
                .containsExactly(latestId, olderId);
    }

    @Test
    void findByIdAndMemberId_returnsNotificationOnlyForOwner() {
        Long ownerId = insertMember();
        Long otherMemberId = insertMember();
        Long notificationId = insertNotification(
                ownerId,
                "소유자 알림",
                OffsetDateTime.parse("2026-08-05T10:00:00+09:00"),
                null);

        assertThat(notificationRepository.findByIdAndMemberId(notificationId, ownerId))
                .isPresent();
        assertThat(notificationRepository.findByIdAndMemberId(notificationId, otherMemberId))
                .isEmpty();
    }

    @Test
    void countByMemberIdAndReadAtIsNull_countsOnlyUnreadNotificationsForMember() {
        Long memberId = insertMember();
        Long otherMemberId = insertMember();
        OffsetDateTime baseTime = OffsetDateTime.parse("2026-08-05T10:00:00+09:00");
        insertNotification(memberId, "안 읽음 1", baseTime, null);
        insertNotification(memberId, "안 읽음 2", baseTime.plusMinutes(1), null);
        insertNotification(memberId, "읽음", baseTime.plusMinutes(2), baseTime.plusMinutes(3));
        insertNotification(otherMemberId, "다른 회원 안 읽음", baseTime, null);

        long count = notificationRepository.countByMemberIdAndReadAtIsNull(memberId);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void insertIfAbsent_insertsSameEventOnlyOnce() {
        Long memberId = insertMember();
        UUID eventId = UUID.fromString("b9d8a554-940f-4d72-b6de-711616158aad");
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-05T10:00:00+09:00");

        int firstResult = notificationRepository.insertIfAbsent(
                eventId,
                memberId,
                "EVENT_SCHEDULE_CHANGED",
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.",
                createdAt);
        int duplicateResult = notificationRepository.insertIfAbsent(
                eventId,
                memberId,
                "EVENT_SCHEDULE_CHANGED",
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.",
                createdAt);

        assertThat(firstResult).isEqualTo(1);
        assertThat(duplicateResult).isZero();
        assertThat(notificationRepository.findByEventId(eventId)).isPresent();
        assertThat(notificationRepository.count()).isEqualTo(1L);
    }

    private Long insertMember() {
        String uniqueValue = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
                INSERT INTO members (
                    email, nickname, oauth_provider, oauth_subject,
                    platform_role, status, created_at, updated_at
                )
                VALUES (?, ?, 'GOOGLE', ?, 'USER', 'ACTIVE', ?, ?)
                RETURNING id
                """,
                Long.class,
                uniqueValue + "@example.com",
                "test-" + uniqueValue,
                "oauth-" + uniqueValue,
                now,
                now);
    }

    private Long insertNotification(
            Long memberId,
            String title,
            OffsetDateTime createdAt,
            OffsetDateTime readAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notifications (
                    event_id, member_id, notification_type, reference_type,
                    reference_id, title, content, read_at, created_at
                )
                VALUES (?, ?, 'EVENT_SCHEDULE_CHANGED', 'EVENT', 10, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                UUID.randomUUID(),
                memberId,
                title,
                title + " 내용",
                readAt,
                createdAt);
    }
}
