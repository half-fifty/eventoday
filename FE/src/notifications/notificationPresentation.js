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
    default:
      return null;
  }
};

export {
  formatNotificationTime,
  getNotificationMeta,
  getNotificationTarget,
};
