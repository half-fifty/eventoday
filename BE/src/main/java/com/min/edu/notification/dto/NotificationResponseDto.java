package com.min.edu.notification.dto;

import java.time.OffsetDateTime;

import com.min.edu.notification.domain.Notification;
import com.min.edu.notification.domain.NotificationType;

public record NotificationResponseDto(
        Long id,
        NotificationType notificationType,
        String referenceType,
        Long referenceId,
        String title,
        String content,
        boolean read,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
    public static NotificationResponseDto from(
            Notification notification
    ) {
        return new NotificationResponseDto(
                notification.getId(),
                notification.getNotificationType(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getTitle(),
                notification.getContent(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}