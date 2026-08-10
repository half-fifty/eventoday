package com.min.edu.notification.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.notification.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO notifications (
                event_id, member_id, notification_type, reference_type,
                reference_id, title, content, created_at
            )
            VALUES (
                :eventId, :memberId, :notificationType, :referenceType,
                :referenceId, :title, :content, :createdAt
            )
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("memberId") Long memberId,
            @Param("notificationType") String notificationType,
            @Param("referenceType") String referenceType,
            @Param("referenceId") Long referenceId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("createdAt") OffsetDateTime createdAt);

    /**
     * ⭐ 멱등성 알림 저장 (Long eventId 기반)
     *
     * idempotencyKey + memberId 기반 결정적 eventId를 사용하여 중복 알림 방지
     * eventId를 UUID로 변환하여 기존 unique constraint 활용
     * 같은 eventId면 ON CONFLICT DO NOTHING으로 중복 방지
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO notifications (
                event_id, member_id, notification_type, reference_type,
                reference_id, title, content, created_at
            )
            VALUES (
                CAST(:eventId || '-0000-0000-0000-000000000000' AS uuid),
                :memberId, :notificationType, :referenceType,
                :referenceId, :title, :content, :createdAt
            )
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsentWithLongEventId(
            @Param("eventId") Long eventId,
            @Param("memberId") Long memberId,
            @Param("notificationType") String notificationType,
            @Param("referenceType") String referenceType,
            @Param("referenceId") Long referenceId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("createdAt") OffsetDateTime createdAt);

    Optional<Notification> findByEventId(UUID eventId);

    Page<Notification> findByMemberIdOrderByCreatedAtDescIdDesc(
            Long memberId,
            Pageable pageable);

    Optional<Notification> findByIdAndMemberId(
            Long notificationId,
            Long memberId);

    long countByMemberIdAndReadAtIsNull(Long memberId);
}