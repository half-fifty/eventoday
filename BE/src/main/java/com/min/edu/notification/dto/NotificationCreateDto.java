package com.min.edu.notification.dto;

import com.min.edu.notification.domain.NotificationType;

public record NotificationCreateDto(
        Long memberId,
        NotificationType notificationType,
        String referenceType,
        Long referenceId,
        String title,
        String content) {

}
