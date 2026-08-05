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

const listPublicVenueMap = async (eventId, mapType) => {
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
  listPublicVenueMap,
  createVenueMap,
  publishVenueMap,
  deleteVenueMap,
  upsertPositions,
};
