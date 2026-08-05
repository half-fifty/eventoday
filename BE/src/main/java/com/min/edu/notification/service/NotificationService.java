package com.min.edu.notification.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.notification.domain.Notification;
import com.min.edu.notification.dto.NotificationCreateDto;
import com.min.edu.notification.dto.NotificationResponseDto;
import com.min.edu.notification.dto.NotificationUnreadCountResponseDto;
import com.min.edu.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public Optional<NotificationResponseDto> createIfAbsent(NotificationCreateDto dto) {
        Notification notification = Notification.create(
                dto.eventId(),
                dto.memberId(),
                dto.notificationType(),
                dto.referenceType(),
                dto.referenceId(),
                dto.title(),
                dto.content());
        int inserted = notificationRepository.insertIfAbsent(
                notification.getEventId(),
                notification.getMemberId(),
                notification.getNotificationType().name(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getCreatedAt());

        if (inserted == 0) {
            return Optional.empty();
        }

        Notification savedNotification = notificationRepository
                .findByEventId(dto.eventId())
                .orElseThrow(() -> new IllegalStateException("저장된 알림을 조회할 수 없습니다."));
        return Optional.of(NotificationResponseDto.from(savedNotification));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getNotifications(Long memberId, Pageable pageable) {
        return notificationRepository.findByMemberIdOrderByCreatedAtDescIdDesc(memberId, pageable)
                .map(NotificationResponseDto::from);
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponseDto getUnreadCount(Long memberId) {
        long count = notificationRepository.countByMemberIdAndReadAtIsNull(memberId);
        return new NotificationUnreadCountResponseDto(count);
    }

    public NotificationResponseDto markAsRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
        return NotificationResponseDto.from(notification);
    }

}
