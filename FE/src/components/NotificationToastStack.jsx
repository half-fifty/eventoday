import { useEffect } from "react";

import Icon from "./Icon.jsx";

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
};

const DEFAULT_META = {
  icon: "notifications",
  label: "새 알림",
  iconClass: "bg-surface-container text-on-surface-variant",
  accentClass: "bg-tertiary",
};

const formatReceivedTime = (createdAt) => {
  if (!createdAt) {
    return "방금 전";
  }

  const date = new Date(createdAt);

  if (Number.isNaN(date.getTime())) {
    return "방금 전";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
};

function NotificationToast({ notification, onDismiss }) {
  const meta =
    NOTIFICATION_META[notification.notificationType] ?? DEFAULT_META;

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      onDismiss(notification.toastKey);
    }, 6000);

    return () => window.clearTimeout(timeoutId);
  }, [notification.toastKey, onDismiss]);

  return (
    <article
      className="notification-toast pointer-events-auto relative w-full overflow-hidden rounded-2xl border border-white/80 bg-white/95 shadow-[0_18px_55px_rgba(25,33,50,0.18)] backdrop-blur-xl"
      role="status"
    >
      <div className={`absolute inset-y-0 left-0 w-1 ${meta.accentClass}`} />

      <div className="flex items-start gap-sm px-md py-[15px] pl-[21px]">
        <div
          className={`mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${meta.iconClass}`}
          aria-hidden="true"
        >
          <Icon name={meta.icon} className="text-[21px]" fill />
        </div>

        <div className="min-w-0 flex-1">
          <div className="mb-1 flex items-center gap-2 text-[11px] font-semibold tracking-[0.04em] text-ink-muted">
            <span>{meta.label}</span>
            <span className="h-0.5 w-0.5 rounded-full bg-outline-variant" />
            <time dateTime={notification.createdAt ?? undefined}>
              {formatReceivedTime(notification.createdAt)}
            </time>
          </div>

          <h2 className="truncate text-[15px] font-bold leading-5 tracking-[-0.01em] text-on-surface">
            {notification.title}
          </h2>
          <p className="mt-1 line-clamp-2 text-[13px] leading-[1.45] text-on-surface-variant">
            {notification.content}
          </p>
        </div>

        <button
          type="button"
          onClick={() => onDismiss(notification.toastKey)}
          className="-mr-1 -mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-ink-muted transition-colors hover:bg-surface-container hover:text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-container"
          aria-label="알림 닫기"
        >
          <Icon name="close" className="text-[18px]" />
        </button>
      </div>

      <div className="notification-toast-progress absolute bottom-0 left-1 h-[2px] bg-primary-container/45" />
    </article>
  );
}

export default function NotificationToastStack({ notifications, onDismiss }) {
  return (
    <aside
      className="pointer-events-none fixed inset-x-0 top-[60px] z-[220] flex flex-col items-end gap-3 px-4 sm:left-auto sm:right-5 sm:w-[390px] sm:px-0"
      aria-label="새 알림"
      aria-live="polite"
      aria-atomic="false"
    >
      {notifications.map((notification) => (
        <NotificationToast
          key={notification.toastKey}
          notification={notification}
          onDismiss={onDismiss}
        />
      ))}
    </aside>
  );
}
