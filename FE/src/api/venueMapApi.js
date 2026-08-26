import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

const listVenueMaps = async (eventId) => {
  const response = await apiRequest(`/events/${eventId}/venue-maps`);
  return response.data;
};

// 같은 mapType이라도 층별로 각각 게시될 수 있어 게시된 평면도 목록을 반환한다.
const listPublicVenueMaps = async (eventId, mapType) => {
  const response = await apiRequest(`/events/${eventId}/venue-maps/public?mapType=${encodeURIComponent(mapType)}`);
  return response.data;
};

const createVenueMap = async (eventId, payload) => {
  const response = await apiRequest(`/events/${eventId}/venue-maps`, json("POST", payload));
  return response.data;
};

const publishVenueMap = async (eventId, mapId) => {
  const response = await apiRequest(`/events/${eventId}/venue-maps/${mapId}/publish`, json("PATCH"));
  return response.data;
};

const deleteVenueMap = async (eventId, mapId) => {
  await apiRequest(`/events/${eventId}/venue-maps/${mapId}`, { method: "DELETE" });
};

// 좌표를 저장하지 않는 "제안" 목록만 반환한다 - 관리자가 확인 후 upsertPositions로 확정해야 한다.
const suggestAutoLayout = async (eventId, mapId) => {
  const response = await apiRequest(
    `/events/${eventId}/venue-maps/${mapId}/auto-layout-suggestions`,
    json("POST")
  );
  return response.data;
};

const upsertPositions = async (eventId, mapId, positions) => {
  const response = await apiRequest(
    `/events/${eventId}/venue-maps/${mapId}/positions`,
    json("PUT", { positions })
  );
  return response.data;
};

// 최근 10분 QR스캔 수 기준 혼잡도(HIGH/MEDIUM/LOW)를 포함한 게시된 평면도 마커 목록.
// ApiResponse 래핑 없이 그대로 반환한다.
const getVenueMapMarkersWithCongestion = async (eventId, mapType) => {
  return apiRequest(`/events/${eventId}/guide/markers?mapType=${encodeURIComponent(mapType)}`);
};

export {
  listVenueMaps,
  listPublicVenueMaps,
  createVenueMap,
  publishVenueMap,
  deleteVenueMap,
  suggestAutoLayout,
  upsertPositions,
  getVenueMapMarkersWithCongestion,
};
