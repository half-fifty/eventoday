package com.min.edu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.notification.domain.Notification;
import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.dto.NotificationCreateDto;
import com.min.edu.notification.dto.NotificationResponseDto;
import com.min.edu.notification.dto.NotificationUnreadCountResponseDto;
import com.min.edu.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID EVENT_ID =
            UUID.fromString("b9d8a554-940f-4d72-b6de-711616158aad");

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createIfAbsent_savesNotificationAndReturnsResponse() {
        NotificationCreateDto createDto = createDto(1L);
        Notification savedNotification = createNotification(1L, "일정 변경");
        givenInsertResult(createDto, 1);
        given(notificationRepository.findByEventId(EVENT_ID))
                .willReturn(Optional.of(savedNotification));

        Optional<NotificationResponseDto> result =
                notificationService.createIfAbsent(createDto);

        assertThat(result).isPresent();
        NotificationResponseDto response = result.orElseThrow();
        assertThat(response.read()).isFalse();
        assertThat(response.readAt()).isNull();
        assertThat(response.createdAt()).isNotNull();
        verify(notificationRepository).findByEventId(EVENT_ID);
    }

    @Test
    void createIfAbsent_returnsEmptyWhenEventWasAlreadyProcessed() {
        NotificationCreateDto createDto = createDto(1L);
        givenInsertResult(createDto, 0);

        Optional<NotificationResponseDto> result =
                notificationService.createIfAbsent(createDto);

        assertThat(result).isEmpty();
        verify(notificationRepository, never()).findByEventId(any(UUID.class));
    }

    @Test
    void getNotifications_returnsOnlyRequestedMemberPage() {
        PageRequest pageable = PageRequest.of(1, 5);
        Notification notification = createNotification(1L, "알림");
        given(notificationRepository.findByMemberIdOrderByCreatedAtDesc(1L, pageable))
                .willReturn(new PageImpl<>(List.of(notification), pageable, 6));

        Page<NotificationResponseDto> response =
                notificationService.getNotifications(1L, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().title()).isEqualTo("알림");
        assertThat(response.getNumber()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(5);
        assertThat(response.getTotalElements()).isEqualTo(6);
        verify(notificationRepository)
                .findByMemberIdOrderByCreatedAtDesc(1L, pageable);
    }

    @Test
    void getUnreadCount_returnsRepositoryCount() {
        given(notificationRepository.countByMemberIdAndReadAtIsNull(1L))
                .willReturn(3L);

        NotificationUnreadCountResponseDto response =
                notificationService.getUnreadCount(1L);

        assertThat(response.count()).isEqualTo(3L);
        verify(notificationRepository).countByMemberIdAndReadAtIsNull(1L);
    }

    @Test
    void markAsRead_marksOwnedNotificationAsRead() {
        Notification notification = createNotification(1L, "알림");
        given(notificationRepository.findByIdAndMemberId(7L, 1L))
                .willReturn(Optional.of(notification));

        NotificationResponseDto response = notificationService.markAsRead(1L, 7L);

        assertThat(notification.isRead()).isTrue();
        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isNotNull();
        verify(notificationRepository).findByIdAndMemberId(7L, 1L);
    }

    @Test
    void markAsRead_throwsNotFoundWhenNotificationDoesNotExist() {
        given(notificationRepository.findByIdAndMemberId(99L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void markAsRead_throwsNotFoundWhenNotificationBelongsToAnotherMember() {
        given(notificationRepository.findByIdAndMemberId(7L, 2L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(2L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.NOTIFICATION_NOT_FOUND);

        verify(notificationRepository).findByIdAndMemberId(7L, 2L);
    }

    private NotificationCreateDto createDto(Long memberId) {
        return new NotificationCreateDto(
                EVENT_ID,
                memberId,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.");
    }

    private Notification createNotification(Long memberId, String title) {
        return Notification.create(
                EVENT_ID,
                memberId,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                title,
                "행사 일정이 변경되었습니다.");
    }

    private void givenInsertResult(NotificationCreateDto dto, int result) {
        given(notificationRepository.insertIfAbsent(
                eq(dto.eventId()),
                eq(dto.memberId()),
                eq(dto.notificationType().name()),
                eq(dto.referenceType()),
                eq(dto.referenceId()),
                eq(dto.title()),
                eq(dto.content()),
                any(OffsetDateTime.class)))
                .willReturn(result);
    }
}
