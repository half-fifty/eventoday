import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";

import { API_BASE_URL } from "../api/apiClient.js";
import NotificationToastStack from "../components/NotificationToastStack.jsx";
import useAuth from "../hooks/useAuth.js";

const MAX_VISIBLE_TOASTS = 3;
const MAX_REMEMBERED_NOTIFICATION_IDS = 100;

export default function NotificationSseProvider({ children }) {
  const { isAuthenticated, loading } = useAuth();
  const eventSourceRef = useRef(null);
  const seenNotificationIdsRef = useRef(new Set());
  const [notifications, setNotifications] = useState([]);

  const dismissNotification = useCallback((toastKey) => {
    setNotifications((currentNotifications) =>
      currentNotifications.filter(
        (notification) => notification.toastKey !== toastKey
      )
    );
  }, []);

  const showNotification = useCallback((notification) => {
    const notificationId = notification.id?.toString();

    if (
      notificationId &&
      seenNotificationIdsRef.current.has(notificationId)
    ) {
      return;
    }

    if (notificationId) {
      seenNotificationIdsRef.current.add(notificationId);

      if (
        seenNotificationIdsRef.current.size >
        MAX_REMEMBERED_NOTIFICATION_IDS
      ) {
        const oldestId = seenNotificationIdsRef.current.values().next().value;
        seenNotificationIdsRef.current.delete(oldestId);
      }
    }

    const toastKey = notificationId ?? crypto.randomUUID();

    setNotifications((currentNotifications) => [
      ...currentNotifications,
      { ...notification, toastKey },
    ].slice(-MAX_VISIBLE_TOASTS));
  }, []);

  const disconnect = useCallback(() => {
    if (eventSourceRef.current === null) {
      return;
    }

    eventSourceRef.current.close();
    eventSourceRef.current = null;
  }, []);

  const connect = useCallback(() => {
    if (loading || !isAuthenticated) {
      return;
    }

    const currentEventSource = eventSourceRef.current;

    if (
      currentEventSource !== null &&
      currentEventSource.readyState !== EventSource.CLOSED
    ) {
      return;
    }

    const normalizedBaseUrl = API_BASE_URL.replace(/\/$/, "");
    const eventSource = new EventSource(
      `${normalizedBaseUrl}/notifications/stream`,
      { withCredentials: true }
    );

    eventSource.addEventListener("notification", (event) => {
      try {
        showNotification(JSON.parse(event.data));
      } catch (error) {
        console.error("알림 데이터를 해석하지 못했습니다.", error);
      }
    });

    eventSourceRef.current = eventSource;
  }, [isAuthenticated, loading, showNotification]);

  useEffect(() => {
    if (loading || !isAuthenticated) {
      disconnect();
      setNotifications([]);
      seenNotificationIdsRef.current.clear();
      return undefined;
    }

    connect();

    const handlePageHide = () => {
      disconnect();
    };

    const handlePageShow = (event) => {
      if (event.persisted) {
        connect();
      }
    };

    window.addEventListener("pagehide", handlePageHide);
    window.addEventListener("pageshow", handlePageShow);

    return () => {
      window.removeEventListener("pagehide", handlePageHide);
      window.removeEventListener("pageshow", handlePageShow);
      disconnect();
    };
  }, [connect, disconnect, isAuthenticated, loading]);

  return (
    <>
      {children}
      <NotificationToastStack
        notifications={notifications}
        onDismiss={dismissNotification}
      />
    </>
  );
}
