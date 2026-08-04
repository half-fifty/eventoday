package com.min.edu.notification.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.notification.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByMemberIdOrderByCreatedAtDesc(
            Long memberId,
            Pageable pageable);

    Optional<Notification> findByIdAndMemberId(
            Long notificationId,
            Long memberId);
}
