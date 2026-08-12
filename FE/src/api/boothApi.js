import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

const listBooths = async (eventId, params = {}) => {
  const query = new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([, value]) => value !== "" && value != null))
  );
  const queryString = query.toString();
  const response = await apiRequest(`/events/${eventId}/booths${queryString ? `?${queryString}` : ""}`);
  return response.data;
};

const listPublicBooths = async (eventId, params = {}) => {
  const query = new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([, value]) => value !== "" && value != null))
  );
  const queryString = query.toString();
  const response = await apiRequest(`/events/${eventId}/booths/public${queryString ? `?${queryString}` : ""}`);
  return response.data;
};

// /booths/public 엔드포인트는 status 필터를 지원하지 않아, 전체 부스가 필요한 화면(참가 부스 목록,
// 신청서-부스 매칭)은 페이지를 끝까지 순회해서 모아야 한다. 한 페이지만 가정하면 100건을 넘는
// 행사에서 결과가 조용히 잘린다.
const listAllPublicBooths = async (eventId, size = 100) => {
  const first = await listPublicBooths(eventId, { page: 0, size });
  const content = [...(first?.content ?? [])];
  const totalPages = first?.totalPages ?? 1;

  for (let page = 1; page < totalPages; page += 1) {
    const next = await listPublicBooths(eventId, { page, size });
    content.push(...(next?.content ?? []));
  }

  return content;
};

const getBooth = async (eventId, boothId) => {
  const response = await apiRequest(`/events/${eventId}/booths/${boothId}`);
  return response.data;
};

const createBooth = async (eventId, payload) => {
  const response = await apiRequest(`/events/${eventId}/booths`, json("POST", payload));
  return response.data;
};

const createBoothsBulk = async (eventId, payload) => {
  const response = await apiRequest(`/events/${eventId}/booths/bulk`, json("POST", payload));
  return response.data;
};

const updateBooth = async (eventId, boothId, payload) => {
  const response = await apiRequest(`/events/${eventId}/booths/${boothId}`, json("PATCH", payload));
  return response.data;
};

const updateBoothStatus = async (eventId, boothId, status) => {
  const response = await apiRequest(
    `/events/${eventId}/booths/${boothId}/status`,
    json("PATCH", { status })
  );
  return response.data;
};

const deleteBooth = async (eventId, boothId) => {
  await apiRequest(`/events/${eventId}/booths/${boothId}`, { method: "DELETE" });
};

const updateBoothIntro = async (eventId, boothId, payload) => {
  const response = await apiRequest(`/events/${eventId}/booths/${boothId}/intro`, json("PATCH", payload));
  return response.data;
};

const issueBoothQr = async (eventId, boothId) => {
  const response = await apiRequest(`/events/${eventId}/booths/${boothId}/qr`, json("POST"));
  return response.data;
};

const getBoothQr = async (eventId, boothId) => {
  const response = await apiRequest(`/events/${eventId}/booths/${boothId}/qr`);
  return response.data;
};

// QR 스캔으로 접근하는 공개 부스 상세 (비로그인도 조회 가능).
const getGuideBoothDetail = async (eventId, boothId) => {
  const response = await apiRequest(`/events/${eventId}/guide/booths/${boothId}`);
  return response;
};

// 혼잡도 기반 부스 추천 (한산한 추천 부스 + 혼잡한 부스). ApiResponse 래핑 없이 그대로 반환한다.
const getRecommendedBooths = async (eventId, params = {}) => {
  const query = new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([, value]) => value !== "" && value != null))
  );
  const queryString = query.toString();
  return apiRequest(`/events/${eventId}/recommended-booths${queryString ? `?${queryString}` : ""}`);
};

// 모바일 부스 검색 (비로그인도 조회 가능, 로그인 시 isInterested 포함). ApiResponse 래핑 없이 Page를 그대로 반환한다.
const searchGuideBooths = async (eventId, params = {}) => {
  const query = new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([, value]) => value !== "" && value != null))
  );
  const queryString = query.toString();
  return apiRequest(`/events/${eventId}/guide/booths${queryString ? `?${queryString}` : ""}`);
};

const addBoothInterest = (boothId) => apiRequest(`/booths/${boothId}/interests`, json("POST"));

const removeBoothInterest = (boothId) => apiRequest(`/booths/${boothId}/interests`, { method: "DELETE" });

// 관심 등록한 부스의 빈자리 알림 수신 여부 켜기/끄기 (관심 등록이 먼저 되어 있어야 함).
const updateVacancyNotification = (boothId, enabled) =>
  apiRequest(`/booths/${boothId}/interests/vacancy-notification`, json("PATCH", { enabled }));

// 이 엔드포인트는 다른 목록 API와 달리 ApiResponse({ data: ... }) 래핑 없이
// 목록을 그대로 반환한다.
const getMyInterests = async () => apiRequest("/booths/interests");

export {
  listBooths,
  listPublicBooths,
  listAllPublicBooths,
  getBooth,
  createBooth,
  createBoothsBulk,
  updateBooth,
  updateBoothStatus,
  deleteBooth,
  updateBoothIntro,
  issueBoothQr,
  getBoothQr,
  getGuideBoothDetail,
  getRecommendedBooths,
  searchGuideBooths,
  addBoothInterest,
  removeBoothInterest,
  updateVacancyNotification,
  getMyInterests,
};
