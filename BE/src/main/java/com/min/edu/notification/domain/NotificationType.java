package com.min.edu.notification.domain;

public enum NotificationType {
    BOOTH_RESERVATION_REMINDER, //부스 예약이 가까워졌다는 알림
    BOOTH_VACANCY_AVAILABLE, // 예약 취소로 빈잦리가 생겼다는 알림
    EVENT_SCHEDULE_CHANGED, // 행사 일정이 변경되었다는 알림
    EVENT_APPROVED,
    EVENT_REJECTED,
    BOOTH_APPLICATION_APPROVED, // 부스 신청 승인 알림
    BOOTH_APPLICATION_REJECTED, // 부스 신청 반려 알림
    BOOTH_REVIEW_REPLIED, // 내 리뷰에 부스 담당자 답글이 달렸다는 알림
    BOOTH_REVIEW_HIDDEN, // 내 리뷰가 운영자 조치로 숨김 처리되었다는 알림
    BOOTH_REVIEW_DELETED_BY_REPORT, // 내 리뷰가 신고 누적으로 삭제되었다는 알림
    BOOTH_REVIEW_REPORT_RESULT // 내가 넣은 신고가 접수/처리되었다는 알림 (신고자용)
}
