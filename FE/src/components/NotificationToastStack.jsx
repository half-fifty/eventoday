import { useEffect } from "react";

import Icon from "./Icon.jsx";
import {
  formatNotificationTime,
  getNotificationMeta,
} from "../notifications/notificationPresentation.js";

function NotificationToast({ notification, onDismiss, onSelect }) {
  const meta = getNotificationMeta(notification.notificationType);

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

      <button
        type="button"
        onClick={() => onSelect(notification)}
        className="flex w-full items-start gap-sm px-md py-[15px] pl-[21px] pr-12 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary-container"
        aria-label={`${notification.title} 알림 확인`}
      >
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
              {formatNotificationTime(notification.createdAt)}
            </time>
          </div>

          <h2 className="truncate text-[15px] font-bold leading-5 tracking-[-0.01em] text-on-surface">
            {notification.title}
          </h2>
          <p className="mt-1 line-clamp-2 text-[13px] leading-[1.45] text-on-surface-variant">
            {notification.content}
          </p>
        </div>
      </button>

      <button
        type="button"
        onClick={() => onDismiss(notification.toastKey)}
        className="absolute right-2 top-2 flex h-8 w-8 items-center justify-center rounded-full text-ink-muted transition-colors hover:bg-surface-container hover:text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-container"
        aria-label="알림 닫기"
      >
        <Icon name="close" className="text-[18px]" />
      </button>

      <div className="notification-toast-progress absolute bottom-0 left-1 h-[2px] bg-primary-container/45" />
    </article>
  );
}

export default function NotificationToastStack({
  notifications,
  onDismiss,
  onSelect,
}) {
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
          onSelect={onSelect}
        />
      ))}
    </aside>
  );
}
