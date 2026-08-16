import { apiRequest } from "./apiClient.js";

// platformAdminApi.js와 동일한 JSON 요청 헬퍼
const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

// 값이 있는 파라미터만 쿼리스트링으로 만든다 (eventApi.list와 동일한 방식)
const toQuery = (params = {}) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") search.set(key, value);
  });
  const query = search.toString();
  return query ? `?${query}` : "";
};

// 플랫폼(사이트 전체) 공지 API
// - list·detail은 공개 API (비로그인 포함)
// - create/update/remove는 PLATFORM_ADMIN 전용 (권한 검증은 BE 서비스 레이어에서 수행)
export const platformNoticeApi = {
  // params: { page, size, keyword } — 생략하면 서버 기본값(0페이지 20건)
  list: (params) => apiRequest(`/platform-notices${toQuery(params)}`),
  // noticeId는 주소창에서 온 값일 수 있어 경로에 넣기 전에 인코딩한다
  detail: (noticeId) => apiRequest(`/platform-notices/${encodeURIComponent(noticeId)}`),
  create: (data) => apiRequest("/v1/admin/platform/notices", json("POST", data)),
  update: (noticeId, data) =>
    apiRequest(`/v1/admin/platform/notices/${noticeId}`, json("PATCH", data)),
  remove: (noticeId) =>
    apiRequest(`/v1/admin/platform/notices/${noticeId}`, { method: "DELETE" }),
};
