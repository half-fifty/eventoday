package com.min.edu.notification.domain;

public enum NotificationType {
    BOOTH_RESERVATION_REMINDER, //부스 예약이 가까워졌다는 알림
    BOOTH_VACANCY_AVAILABLE, // 예약 취소로 빈잦리가 생겼다는 알림
    EVENT_SCHEDULE_CHANGED, // 행사 일정이 변경되었다는 알림
    EVENT_APPROVED,
    EVENT_REJECTED,
    BOOTH_APPLICATION_APPROVED, // 부스 신청 승인 알림
    BOOTH_APPLICATION_REJECTED  // 부스 신청 반려 알림
}
