import { useEffect, useRef } from "react";

import Icon from "./Icon.jsx";
import {
  formatNotificationTime,
  getNotificationMeta,
} from "../notifications/notificationPresentation.js";

const FOCUSABLE_ELEMENT_SELECTOR = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  '[tabindex]:not([tabindex="-1"])',
].join(",");

function NotificationListItem({ notification, onSelect }) {
  const meta = getNotificationMeta(notification.notificationType);

  return (
    <button
      type="button"
      onClick={() => onSelect(notification)}
      className={`group relative flex w-full items-start gap-sm border-b border-divider-soft px-md py-[15px] text-left transition-colors last:border-b-0 hover:bg-surface-container-low ${
        notification.read ? "bg-white" : "bg-[#f3f8ff]"
      }`}
    >
      <div
        className={`mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${meta.iconClass}`}
        aria-hidden="true"
      >
        <Icon name={meta.icon} className="text-[20px]" fill />
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-semibold text-ink-muted">
            {meta.label}
          </span>
          <span className="h-0.5 w-0.5 rounded-full bg-outline-variant" />
          <time
            dateTime={notification.createdAt ?? undefined}
            className="text-[11px] text-ink-muted"
          >
            {formatNotificationTime(notification.createdAt)}
          </time>
        </div>

        <p className={`mt-1 truncate text-[14px] leading-5 text-on-surface ${
          notification.read ? "font-medium" : "font-bold"
        }`}>
          {notification.title}
        </p>
        <p className="mt-0.5 line-clamp-2 text-[12px] leading-[1.45] text-on-surface-variant">
          {notification.content}
        </p>
      </div>

      {!notification.read && (
        <span
          className="mt-2 h-2 w-2 shrink-0 rounded-full bg-primary-container shadow-[0_0_0_3px_rgba(0,102,204,0.10)]"
          aria-label="읽지 않음"
        />
      )}

      <Icon
        name="chevron_right"
        className="absolute bottom-3 right-3 text-[17px] text-outline-variant opacity-0 transition-opacity group-hover:opacity-100"
      />
    </button>
  );
}

export default function NotificationPanel({
  isOpen,
  notifications,
  unreadCount,
  loading,
  loadingMore,
  hasMore,
  error,
  onClose,
  onSelect,
  onLoadMore,
}) {
  const panelRef = useRef(null);
  const closeButtonRef = useRef(null);
  const previousFocusRef = useRef(null);

  useEffect(() => {
    if (!isOpen) {
      return undefined;
    }

    previousFocusRef.current = document.activeElement;
    const focusFrame = window.requestAnimationFrame(() => {
      closeButtonRef.current?.focus();
    });

    const handleKeyDown = (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }

      if (event.key !== "Tab") {
        return;
      }

      const panel = panelRef.current;
      if (panel === null) {
        return;
      }

      const focusableElements = Array.from(
        panel.querySelectorAll(FOCUSABLE_ELEMENT_SELECTOR)
      );

      if (focusableElements.length === 0) {
        event.preventDefault();
        panel.focus();
        return;
      }

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      const focusIsOutsidePanel = !panel.contains(document.activeElement);

      if (
        event.shiftKey &&
        (document.activeElement === firstElement || focusIsOutsidePanel)
      ) {
        event.preventDefault();
        lastElement.focus();
        return;
      }

      if (
        !event.shiftKey &&
        (document.activeElement === lastElement || focusIsOutsidePanel)
      ) {
        event.preventDefault();
        firstElement.focus();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.cancelAnimationFrame(focusFrame);
      window.removeEventListener("keydown", handleKeyDown);

      const previousFocus = previousFocusRef.current;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
        previousFocus.focus();
      }
      previousFocusRef.current = null;
    };
  }, [isOpen, onClose]);

  if (!isOpen) {
    return null;
  }

  return (
    <>
      <button
        type="button"
        className="fixed inset-0 z-[180] cursor-default bg-black/25 backdrop-blur-[1px]"
        onClick={onClose}
        aria-label="알림 패널 닫기"
      />

      <section
        ref={panelRef}
        tabIndex={-1}
        className="notification-panel-enter fixed inset-x-0 bottom-0 z-[190] flex max-h-[78vh] flex-col overflow-hidden rounded-t-[24px] border border-white/80 bg-white shadow-[0_-18px_60px_rgba(0,0,0,0.2)] sm:bottom-auto sm:left-auto sm:right-5 sm:top-[52px] sm:w-[390px] sm:max-h-[calc(100vh-68px)] sm:rounded-2xl sm:shadow-[0_20px_65px_rgba(25,33,50,0.22)]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="notification-panel-title"
      >
        <div className="mx-auto mt-2 h-1 w-9 rounded-full bg-surface-container-highest sm:hidden" />

        <header className="flex items-center justify-between border-b border-divider-soft px-md py-sm">
          <div className="flex items-center gap-2">
            <h2
              id="notification-panel-title"
              className="text-[17px] font-bold tracking-[-0.02em] text-on-surface"
            >
              알림
            </h2>
            {unreadCount > 0 && (
              <span className="rounded-full bg-primary-fixed px-2 py-0.5 text-[11px] font-bold text-primary">
                {unreadCount > 99 ? "99+" : unreadCount}개 새 알림
              </span>
            )}
          </div>

          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full text-ink-muted transition-colors hover:bg-surface-container hover:text-on-surface"
            aria-label="알림 패널 닫기"
          >
            <Icon name="close" className="text-[19px]" />
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
          {loading && notifications.length === 0 && (
            <div className="space-y-3 p-md" aria-label="알림 불러오는 중">
              {[0, 1, 2].map((item) => (
                <div key={item} className="flex animate-pulse gap-sm">
                  <div className="h-10 w-10 rounded-xl bg-surface-container" />
                  <div className="flex-1 space-y-2 py-1">
                    <div className="h-3 w-24 rounded bg-surface-container" />
                    <div className="h-3 w-full rounded bg-surface-container" />
                    <div className="h-3 w-2/3 rounded bg-surface-container" />
                  </div>
                </div>
              ))}
            </div>
          )}

          {!loading && error && notifications.length === 0 && (
            <div className="flex min-h-[250px] flex-col items-center justify-center px-lg text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-error-container text-error">
                <Icon name="cloud_off" className="text-[23px]" />
              </div>
              <p className="mt-sm text-[14px] font-semibold text-on-surface">
                알림을 불러오지 못했습니다
              </p>
              <p className="mt-1 text-[12px] text-ink-muted">
                잠시 후 다시 열어주세요.
              </p>
            </div>
          )}

          {!loading && !error && notifications.length === 0 && (
            <div className="flex min-h-[280px] flex-col items-center justify-center px-lg text-center">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-surface-container-low text-ink-muted">
                <Icon name="notifications_none" className="text-[27px]" />
              </div>
              <p className="mt-sm text-[14px] font-semibold text-on-surface">
                아직 도착한 알림이 없어요
              </p>
              <p className="mt-1 text-[12px] text-ink-muted">
                예약과 행사 소식을 여기에 알려드릴게요.
              </p>
            </div>
          )}

          {notifications.map((notification) => (
            <NotificationListItem
              key={notification.id}
              notification={notification}
              onSelect={onSelect}
            />
          ))}

          {hasMore && notifications.length > 0 && (
            <div className="border-t border-divider-soft p-sm">
              <button
                type="button"
                onClick={onLoadMore}
                disabled={loadingMore}
                className="w-full rounded-xl py-2.5 text-[13px] font-semibold text-primary transition-colors hover:bg-primary-fixed disabled:cursor-wait disabled:opacity-60"
              >
                {loadingMore ? "불러오는 중..." : "이전 알림 더 보기"}
              </button>
            </div>
          )}
        </div>
      </section>
    </>
  );
}
