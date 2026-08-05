package com.min.edu.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void create_createsUnreadNotification() {
        Notification notification = createNotification();

        assertThat(notification.getMemberId()).isEqualTo(1L);
        assertThat(notification.getNotificationType())
                .isEqualTo(NotificationType.EVENT_SCHEDULE_CHANGED);
        assertThat(notification.getReferenceType()).isEqualTo("EVENT");
        assertThat(notification.getReferenceId()).isEqualTo(10L);
        assertThat(notification.getTitle()).isEqualTo("일정 변경");
        assertThat(notification.getContent()).isEqualTo("행사 일정이 변경되었습니다.");
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getReadAt()).isNull();
        assertThat(notification.getCreatedAt()).isNotNull();
    }

    @Test
    void markAsRead_marksNotificationAsRead() {
        Notification notification = createNotification();

        notification.markAsRead();

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    void markAsRead_keepsFirstReadTimeWhenCalledAgain() {
        Notification notification = createNotification();
        notification.markAsRead();
        OffsetDateTime firstReadAt = notification.getReadAt();

        notification.markAsRead();

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    private Notification createNotification() {
        return Notification.create(
                1L,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.");
    }
}
