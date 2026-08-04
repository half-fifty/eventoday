import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import { API_BASE_URL } from "../api/apiClient.js";
import {
  getNotifications,
  getUnreadCount,
  markAsRead,
} from "../api/notificationApi.js";
import NotificationPanel from "../components/NotificationPanel.jsx";
import NotificationToastStack from "../components/NotificationToastStack.jsx";
import useAuth from "../hooks/useAuth.js";
import { NotificationContext } from "./NotificationContext.jsx";
import { getNotificationTarget } from "./notificationPresentation.js";

const PAGE_SIZE = 20;
const MAX_VISIBLE_TOASTS = 3;
const MAX_REMEMBERED_NOTIFICATION_IDS = 100;

const mergeUniqueNotifications = (currentNotifications, newNotifications) => {
  const notificationMap = new Map();

  [...currentNotifications, ...newNotifications].forEach((notification) => {
    notificationMap.set(String(notification.id), notification);
  });

  return Array.from(notificationMap.values()).sort(
    (first, second) =>
      new Date(second.createdAt).getTime() - new Date(first.createdAt).getTime()
  );
};

const createToastKey = () => {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random()}`;
};

export default function NotificationSseProvider({ children, onNavigate }) {
  const { isAuthenticated, loading } = useAuth();
  const eventSourceRef = useRef(null);
  const panelOpenRef = useRef(false);
  const currentPageRef = useRef(0);
  const seenNotificationIdsRef = useRef(new Set());
  const readInFlightIdsRef = useRef(new Set());

  const [toastNotifications, setToastNotifications] = useState([]);
  const [notificationItems, setNotificationItems] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isPanelOpen, setIsPanelOpen] = useState(false);
  const [isListLoading, setIsListLoading] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [listError, setListError] = useState(false);

  const rememberNotificationId = useCallback((notificationId) => {
    if (!notificationId) {
      return;
    }

    const seenIds = seenNotificationIdsRef.current;
    seenIds.add(String(notificationId));

    if (seenIds.size > MAX_REMEMBERED_NOTIFICATION_IDS) {
      const oldestId = seenIds.values().next().value;
      seenIds.delete(oldestId);
    }
  }, []);

  const fetchInitialNotifications = useCallback(async () => {
    setIsListLoading(true);
    setListError(false);

    try {
      const [pageData, count] = await Promise.all([
        getNotifications({ page: 0, size: PAGE_SIZE }),
        getUnreadCount(),
      ]);
      const content = Array.isArray(pageData?.content) ? pageData.content : [];

      content.forEach((notification) => {
        rememberNotificationId(notification.id);
      });

      setNotificationItems(content);
      setUnreadCount(count);
      setHasMore(pageData?.last === false);
      currentPageRef.current = 0;
    } catch (error) {
      console.error("알림 목록을 불러오지 못했습니다.", error);
      setListError(true);
    } finally {
      setIsListLoading(false);
    }
  }, [rememberNotificationId]);

  const loadMoreNotifications = useCallback(async () => {
    if (isLoadingMore || !hasMore) {
      return;
    }

    const nextPage = currentPageRef.current + 1;
    setIsLoadingMore(true);

    try {
      const pageData = await getNotifications({
        page: nextPage,
        size: PAGE_SIZE,
      });
      const content = Array.isArray(pageData?.content) ? pageData.content : [];

      content.forEach((notification) => {
        rememberNotificationId(notification.id);
      });

      setNotificationItems((currentNotifications) =>
        mergeUniqueNotifications(currentNotifications, content)
      );
      setHasMore(pageData?.last === false);
      currentPageRef.current = nextPage;
    } catch (error) {
      console.error("이전 알림을 불러오지 못했습니다.", error);
    } finally {
      setIsLoadingMore(false);
    }
  }, [hasMore, isLoadingMore, rememberNotificationId]);

  const dismissToast = useCallback((toastKey) => {
    setToastNotifications((currentNotifications) =>
      currentNotifications.filter(
        (notification) => notification.toastKey !== toastKey
      )
    );
  }, []);

  const handleIncomingNotification = useCallback((notification) => {
    const notificationId = notification.id?.toString();

    if (
      notificationId &&
      seenNotificationIdsRef.current.has(notificationId)
    ) {
      return;
    }

    rememberNotificationId(notificationId);
    setNotificationItems((currentNotifications) =>
      mergeUniqueNotifications([notification], currentNotifications)
    );

    if (!notification.read) {
      setUnreadCount((currentCount) => currentCount + 1);
    }

    if (!panelOpenRef.current) {
      setToastNotifications((currentNotifications) => [
        ...currentNotifications,
        {
          ...notification,
          toastKey: notificationId ?? createToastKey(),
        },
      ].slice(-MAX_VISIBLE_TOASTS));
    }
  }, [rememberNotificationId]);

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
        handleIncomingNotification(JSON.parse(event.data));
      } catch (error) {
        console.error("알림 데이터를 해석하지 못했습니다.", error);
      }
    });

    eventSourceRef.current = eventSource;
  }, [handleIncomingNotification, isAuthenticated, loading]);

  const closePanel = useCallback(() => {
    panelOpenRef.current = false;
    setIsPanelOpen(false);
  }, []);

  const openPanel = useCallback(() => {
    panelOpenRef.current = true;
    setIsPanelOpen(true);
    setToastNotifications([]);
    fetchInitialNotifications();
  }, [fetchInitialNotifications]);

  const togglePanel = useCallback(() => {
    if (panelOpenRef.current) {
      closePanel();
      return;
    }

    openPanel();
  }, [closePanel, openPanel]);

  const markNotificationAsRead = useCallback(async (notification) => {
    const notificationId = notification.id?.toString();

    if (
      notification.read ||
      !notificationId ||
      readInFlightIdsRef.current.has(notificationId)
    ) {
      return;
    }

    readInFlightIdsRef.current.add(notificationId);
    setNotificationItems((currentNotifications) =>
      currentNotifications.map((currentNotification) =>
        String(currentNotification.id) === notificationId
          ? { ...currentNotification, read: true, readAt: new Date().toISOString() }
          : currentNotification
      )
    );
    setUnreadCount((currentCount) => Math.max(0, currentCount - 1));

    try {
      const updatedNotification = await markAsRead(notification.id);
      setNotificationItems((currentNotifications) =>
        currentNotifications.map((currentNotification) =>
          String(currentNotification.id) === notificationId
            ? updatedNotification
            : currentNotification
        )
      );
    } catch (error) {
      console.error("알림을 읽음 처리하지 못했습니다.", error);
      fetchInitialNotifications();
    } finally {
      readInFlightIdsRef.current.delete(notificationId);
    }
  }, [fetchInitialNotifications]);

  const selectNotification = useCallback((notification) => {
    if (notification.toastKey) {
      dismissToast(notification.toastKey);
    }

    closePanel();
    markNotificationAsRead(notification);

    const target = getNotificationTarget(notification);

    if (target && onNavigate) {
      onNavigate(target);
    }
  }, [closePanel, dismissToast, markNotificationAsRead, onNavigate]);

  useEffect(() => {
    if (loading || !isAuthenticated) {
      disconnect();
      panelOpenRef.current = false;
      currentPageRef.current = 0;
      seenNotificationIdsRef.current.clear();
      readInFlightIdsRef.current.clear();
      setToastNotifications([]);
      setNotificationItems([]);
      setUnreadCount(0);
      setIsPanelOpen(false);
      setHasMore(false);
      setListError(false);
      return undefined;
    }

    connect();
    fetchInitialNotifications();

    const handlePageHide = () => {
      disconnect();
    };

    const handlePageShow = (event) => {
      if (event.persisted) {
        connect();
        fetchInitialNotifications();
      }
    };

    window.addEventListener("pagehide", handlePageHide);
    window.addEventListener("pageshow", handlePageShow);

    return () => {
      window.removeEventListener("pagehide", handlePageHide);
      window.removeEventListener("pageshow", handlePageShow);
      disconnect();
    };
  }, [connect, disconnect, fetchInitialNotifications, isAuthenticated, loading]);

  const contextValue = useMemo(() => ({
    unreadCount,
    isPanelOpen,
    openPanel,
    closePanel,
    togglePanel,
  }), [closePanel, isPanelOpen, openPanel, togglePanel, unreadCount]);

  return (
    <NotificationContext.Provider value={contextValue}>
      {children}

      <NotificationPanel
        isOpen={isPanelOpen}
        notifications={notificationItems}
        unreadCount={unreadCount}
        loading={isListLoading}
        loadingMore={isLoadingMore}
        hasMore={hasMore}
        error={listError}
        onClose={closePanel}
        onSelect={selectNotification}
        onLoadMore={loadMoreNotifications}
      />

      <NotificationToastStack
        notifications={toastNotifications}
        onDismiss={dismissToast}
        onSelect={selectNotification}
      />
    </NotificationContext.Provider>
  );
}
