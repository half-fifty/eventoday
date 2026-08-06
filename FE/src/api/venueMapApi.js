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

const upsertPositions = async (eventId, mapId, positions) => {
  const response = await apiRequest(
    `/events/${eventId}/venue-maps/${mapId}/positions`,
    json("PUT", { positions })
  );
  return response.data;
};

export {
  listVenueMaps,
  listPublicVenueMaps,
  createVenueMap,
  publishVenueMap,
  deleteVenueMap,
  upsertPositions,
};
