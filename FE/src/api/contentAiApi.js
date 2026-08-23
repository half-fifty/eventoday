import { apiRequest } from "./apiClient.js";

// platformNoticeApi.js와 동일한 JSON 요청 헬퍼
const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

// 공지·자료 AI 작성 보조 API
// AI 키는 서버에서만 다루므로 프론트엔드는 이 엔드포인트를 통해서만 모델을 호출한다.
// signal: 생성이 오래 걸릴 때 화면에서 요청을 끊기 위한 AbortSignal (선택)
export const contentAiApi = {
  // 사이트 공지 (PLATFORM_ADMIN 전용)
  // payload: { action, tone, prompt, title, content }
  generateNotice: (payload, signal) =>
    apiRequest("/v1/admin/ai/content", { ...json("POST", payload), signal }),

  // 행사 공지·자료 (해당 행사 EVENT_MANAGER 또는 PLATFORM_ADMIN)
  // payload: { action, contentType, audience, resourceType, tone, prompt, title, content }
  generateEventContent: (eventId, payload, signal) =>
    apiRequest(`/v1/events/${eventId}/ai/content`, { ...json("POST", payload), signal }),
};
