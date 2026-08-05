package com.min.edu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void create_savesNotificationAndReturnsResponse() {
        NotificationCreateDto createDto = createDto(1L);
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        NotificationResponseDto response = notificationService.create(createDto);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification savedNotification = captor.getValue();

        assertThat(savedNotification.getMemberId()).isEqualTo(1L);
        assertThat(savedNotification.getNotificationType())
                .isEqualTo(NotificationType.EVENT_SCHEDULE_CHANGED);
        assertThat(savedNotification.getReferenceType()).isEqualTo("EVENT");
        assertThat(savedNotification.getReferenceId()).isEqualTo(10L);
        assertThat(savedNotification.getTitle()).isEqualTo("일정 변경");
        assertThat(savedNotification.getContent()).isEqualTo("행사 일정이 변경되었습니다.");
        assertThat(response.read()).isFalse();
        assertThat(response.readAt()).isNull();
        assertThat(response.createdAt()).isNotNull();
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
                memberId,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.");
    }

    private Notification createNotification(Long memberId, String title) {
        return Notification.create(
                memberId,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                title,
                "행사 일정이 변경되었습니다.");
    }
}
