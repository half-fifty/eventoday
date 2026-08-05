import Icon from "./Icon.jsx";
import useAuth from "../hooks/useAuth.js";
import { useNotifications } from "../notifications/NotificationContext.jsx";

export default function NotificationBell() {
  const { isAuthenticated, loading } = useAuth();
  const {
    unreadCount,
    isPanelOpen,
    togglePanel,
  } = useNotifications();
  const badgeText = unreadCount > 99 ? "99+" : String(unreadCount);

  if (loading || !isAuthenticated) {
    return null;
  }

  return (
    <button
      type="button"
      onClick={togglePanel}
      className="relative flex h-8 w-8 items-center justify-center rounded-full text-white/80 transition-colors hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-on-dark"
      aria-label={`알림${unreadCount > 0 ? `, 읽지 않은 알림 ${unreadCount}개` : ""}`}
      aria-expanded={isPanelOpen}
      aria-haspopup="dialog"
    >
      <Icon
        name="notifications"
        className="text-[20px]"
        fill={unreadCount > 0}
      />

      {unreadCount > 0 && (
        <span className="absolute -right-1.5 -top-1 flex min-w-[17px] items-center justify-center rounded-full bg-[#ff3b30] px-1 text-[9px] font-bold leading-[17px] text-white shadow-[0_0_0_2px_#000]">
          {badgeText}
        </span>
      )}
    </button>
  );
}
