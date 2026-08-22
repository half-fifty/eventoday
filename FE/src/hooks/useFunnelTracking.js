import { useCallback, useEffect, useRef } from "react";
import { funnelActionApi } from "../api/funnelActionApi.js";

const SESSION_ID_KEY = "funnelSessionId";
const LAST_ACTIVITY_KEY = "funnelSessionLastActivityAt";
const SESSION_TIMEOUT_MS = 30 * 60 * 1000;

// 30분 무활동 시 세션을 교체한다 (event-contract.md 참고). 브라우저 탭이 닫히면
// sessionStorage도 같이 사라지므로 별도 만료 처리는 필요 없다.
const resolveSessionId = () => {
  const now = Date.now();
  const lastActivity = Number(sessionStorage.getItem(LAST_ACTIVITY_KEY) || 0);
  const expired = now - lastActivity > SESSION_TIMEOUT_MS;
  const existingSessionId = sessionStorage.getItem(SESSION_ID_KEY);
  const sessionId = existingSessionId && !expired ? existingSessionId : crypto.randomUUID();

  sessionStorage.setItem(SESSION_ID_KEY, sessionId);
  sessionStorage.setItem(LAST_ACTIVITY_KEY, String(now));
  return sessionId;
};

const buildPayload = (eventId, actionType, properties) => ({
  sessionId: resolveSessionId(),
  eventId: Number(eventId),
  actionType,
  occurredAt: new Date().toISOString(),
  properties,
});

/**
 * 퍼널 이벤트(사용자 행동) 추적 훅. eventId가 있는 행사 관련 페이지에서만 사용한다.
 * pageViewActionType을 주면 마운트 시 자동 발행하고, 뒤로가기/창닫기도 같이 추적한다.
 * 분석용 호출이라 실패해도 사용자 흐름에 영향 주면 안 되므로 에러는 조용히 무시한다.
 */
export default function useFunnelTracking(eventId, pageViewActionType) {
  const eventIdRef = useRef(eventId);
  eventIdRef.current = eventId;

  const track = useCallback((actionType, properties = {}) => {
    if (!eventIdRef.current) return;
    funnelActionApi
      .collect(buildPayload(eventIdRef.current, actionType, properties))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!eventId || !pageViewActionType) return;
    track(pageViewActionType);
  }, [eventId, pageViewActionType, track]);

  useEffect(() => {
    if (!eventId) return;

    const handlePopState = () => track("BACK_NAVIGATION", { fromPage: window.location.pathname });

    // 창을 닫는 순간엔 일반 fetch가 중간에 끊길 수 있어, 페이지 종료 중에도 전송을
    // 보장해주는 sendBeacon을 쓴다 (event-contract.md 참고).
    const handlePageHide = () => {
      if (!navigator.sendBeacon) return;
      const apiBase = import.meta.env.VITE_API_BASE_URL || "/api";
      const payload = buildPayload(eventIdRef.current, "PAGE_CLOSE", {});
      navigator.sendBeacon(
        `${apiBase}/funnel-actions`,
        new Blob([JSON.stringify(payload)], { type: "application/json" })
      );
    };

    window.addEventListener("popstate", handlePopState);
    window.addEventListener("pagehide", handlePageHide);
    return () => {
      window.removeEventListener("popstate", handlePopState);
      window.removeEventListener("pagehide", handlePageHide);
    };
  }, [eventId, track]);

  return track;
}
