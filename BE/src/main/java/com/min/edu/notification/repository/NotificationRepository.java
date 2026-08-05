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

    Optional<Notification> findByEventId(UUID eventId);

    Page<Notification> findByMemberIdOrderByCreatedAtDescIdDesc(
            Long memberId,
            Pageable pageable);

    Optional<Notification> findByIdAndMemberId(
            Long notificationId,
            Long memberId);

    long countByMemberIdAndReadAtIsNull(Long memberId);
}
