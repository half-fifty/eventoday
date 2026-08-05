package com.min.edu.notification.dto;

import java.util.UUID;

import com.min.edu.notification.domain.NotificationType;

public record NotificationCreateDto(
        UUID eventId,
        Long memberId,
        NotificationType notificationType,
        String referenceType,
        Long referenceId,
        String title,
        String content) {

}
