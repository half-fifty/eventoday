package com.min.edu.notification.dto;

import java.util.UUID;

import com.min.edu.notification.domain.NotificationType;

public record NotificationEventDto(
        UUID eventId,
        Long memberId,
        NotificationType notificationType,
        String referenceType,
        Long referenceId,
        String title,
        String content) {

    public static NotificationEventDto create(
            Long memberId,
            NotificationType notificationType,
            String referenceType,
            Long referenceId,
            String title,
            String content) {
        return new NotificationEventDto(
                UUID.randomUUID(),
                memberId,
                notificationType,
                referenceType,
                referenceId,
                title,
                content);
    }
}
