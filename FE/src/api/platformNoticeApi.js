import { apiRequest } from "./apiClient.js";

// platformAdminApi.js와 동일한 JSON 요청 헬퍼
const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

// 플랫폼(사이트 전체) 공지 API
// - list는 공개 API (비로그인 포함)
// - create/update/remove는 PLATFORM_ADMIN 전용 (권한 검증은 BE 서비스 레이어에서 수행)
export const platformNoticeApi = {
  list: () => apiRequest("/platform-notices"),
  create: (data) => apiRequest("/v1/admin/platform/notices", json("POST", data)),
  update: (noticeId, data) =>
    apiRequest(`/v1/admin/platform/notices/${noticeId}`, json("PATCH", data)),
  remove: (noticeId) =>
    apiRequest(`/v1/admin/platform/notices/${noticeId}`, { method: "DELETE" }),
};
