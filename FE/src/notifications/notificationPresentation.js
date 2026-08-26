const NOTIFICATION_META = {
  BOOTH_RESERVATION_REMINDER: {
    icon: "notifications_active",
    label: "예약 알림",
    iconClass: "bg-[#fff4dd] text-[#9a5b00]",
    accentClass: "bg-[#ffb340]",
  },
  BOOTH_VACANCY_AVAILABLE: {
    icon: "event_seat",
    label: "빈자리 알림",
    iconClass: "bg-[#e8f8ed] text-[#137b3a]",
    accentClass: "bg-[#34c759]",
  },
  EVENT_SCHEDULE_CHANGED: {
    icon: "calendar_clock",
    label: "일정 알림",
    iconClass: "bg-primary-fixed text-primary",
    accentClass: "bg-primary-container",
  },
  EVENT_APPROVED: {
    icon: "task_alt",
    label: "행사 승인",
    iconClass: "bg-[#e8f8ed] text-[#137b3a]",
    accentClass: "bg-[#34c759]",
  },
  EVENT_REJECTED: {
    icon: "error_outline",
    label: "행사 반려",
    iconClass: "bg-error/10 text-error",
    accentClass: "bg-error",
  },
  BOOTH_APPLICATION_APPROVED: {
    icon: "task_alt",
    label: "부스 신청 승인",
    iconClass: "bg-[#e8f8ed] text-[#137b3a]",
    accentClass: "bg-[#34c759]",
  },
  BOOTH_APPLICATION_REJECTED: {
    icon: "error_outline",
    label: "부스 신청 반려",
    iconClass: "bg-error/10 text-error",
    accentClass: "bg-error",
  },
  BOOTH_REVIEW_REPLIED: {
    icon: "forum",
    label: "리뷰 답글",
    iconClass: "bg-primary-fixed text-primary",
    accentClass: "bg-primary-container",
  },
  BOOTH_REVIEW_HIDDEN: {
    icon: "visibility_off",
    label: "리뷰 비공개 처리",
    iconClass: "bg-error/10 text-error",
    accentClass: "bg-error",
  },
  BOOTH_REVIEW_DELETED_BY_REPORT: {
    icon: "delete_forever",
    label: "리뷰 삭제",
    iconClass: "bg-error/10 text-error",
    accentClass: "bg-error",
  },
  BOOTH_REVIEW_REPORT_RESULT: {
    icon: "outlined_flag",
    label: "신고 처리 결과",
    iconClass: "bg-surface-container text-on-surface-variant",
    accentClass: "bg-tertiary",
  },
};

const DEFAULT_NOTIFICATION_META = {
  icon: "notifications",
  label: "새 알림",
  iconClass: "bg-surface-container text-on-surface-variant",
  accentClass: "bg-tertiary",
};

const getNotificationMeta = (notificationType) => {
  return NOTIFICATION_META[notificationType] ?? DEFAULT_NOTIFICATION_META;
};

const formatNotificationTime = (createdAt) => {
  const createdDate = new Date(createdAt);

  if (Number.isNaN(createdDate.getTime())) {
    return "방금 전";
  }

  const elapsedMilliseconds = Date.now() - createdDate.getTime();
  const elapsedMinutes = Math.floor(elapsedMilliseconds / 60000);

  if (elapsedMinutes < 1) {
    return "방금 전";
  }

  if (elapsedMinutes < 60) {
    return `${elapsedMinutes}분 전`;
  }

  const elapsedHours = Math.floor(elapsedMinutes / 60);

  if (elapsedHours < 24) {
    return `${elapsedHours}시간 전`;
  }

  if (elapsedHours < 48) {
    return "어제";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
  }).format(createdDate);
};

const getNotificationTarget = (notification) => {
  if (!notification.referenceType || !notification.referenceId) {
    return null;
  }

  const referenceType = notification.referenceType.toUpperCase();
  if (referenceType.startsWith("EVR:")) {
    const organizationId = referenceType.slice("EVR:".length);
    return `/organizer-admin?organizationId=${encodeURIComponent(organizationId)}&eventId=${notification.referenceId}`;
  }

  switch (referenceType) {
    case "EVENT":
      return `/events/${notification.referenceId}`;
    case "EVENT_REVIEW":
      return `/organizer-admin?eventId=${notification.referenceId}`;
    case "RECRUITMENT":
    case "BOOTH_RECRUITMENT":
      return `/recruitments/${notification.referenceId}`;
    case "BOOTH":
      return `/booth-detail?booth=${notification.referenceId}`;
    case "BOOTH_APPLICATION":
      return "/my-applications"; // 부스 신청 승인/반려 알림 → 내 신청 목록으로 이동
    case "BOOTH_REVIEW":
      // referenceId는 리뷰 id라 boothId/eventId 없이는 부스 상세로 바로 못 보내서,
      // BOOTH_APPLICATION과 같은 패턴으로 내 리뷰가 모여있는 마이페이지로 보낸다.
      return "/mypage";
    default:
      return null;
  }
};

export {
  formatNotificationTime,
  getNotificationMeta,
  getNotificationTarget,
};
