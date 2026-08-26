import { createContext, useCallback, useMemo, useState } from "react";
import Icon from "../components/Icon.jsx";

// 앱 전역에서 "적용되었습니다" 류의 짧은 안내 메시지를 통일된 모양으로 띄우기 위한 프로바이더.
// 알림함(NotificationToastStack)과는 별개다 - 그쪽은 서버가 만든 Notification 엔티티 전용이고,
// 이건 어떤 화면에서든 즉석에서 "방금 한 동작 결과"를 알려줄 때 쓴다.
export const ToastContext = createContext(null);

let toastIdSeq = 0;

const TYPE_META = {
  success: { icon: "check_circle", accentClass: "bg-status-available" },
  error: { icon: "error", accentClass: "bg-error" },
  info: { icon: "info", accentClass: "bg-primary" },
};

export default function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const dismissToast = useCallback((id) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback(
    (message, options = {}) => {
      const id = ++toastIdSeq;
      const type = options.type ?? "success";
      const duration = options.duration ?? 3000;
      setToasts((prev) => [...prev, { id, message, type }]);
      window.setTimeout(() => dismissToast(id), duration);
      return id;
    },
    [dismissToast]
  );

  const value = useMemo(() => ({ showToast }), [showToast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <aside
        className="pointer-events-none fixed inset-x-0 bottom-6 z-[300] flex flex-col items-center gap-2 px-4"
        aria-live="polite"
        aria-atomic="false"
      >
        {toasts.map((toast) => {
          const meta = TYPE_META[toast.type] ?? TYPE_META.success;
          return (
            <div
              key={toast.id}
              role="status"
              className="pointer-events-auto flex max-w-[90vw] items-center gap-sm rounded-full bg-black/85 px-lg py-2.5 text-white shadow-lg backdrop-blur-sm"
            >
              <span className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full ${meta.accentClass}`}>
                <Icon name={meta.icon} className="text-[14px] text-white" />
              </span>
              <span className="text-caption font-body-strong">{toast.message}</span>
            </div>
          );
        })}
      </aside>
    </ToastContext.Provider>
  );
}
