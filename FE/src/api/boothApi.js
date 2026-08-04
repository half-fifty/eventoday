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

export {
  listBooths,
  listPublicBooths,
  getBooth,
  createBooth,
  createBoothsBulk,
  updateBooth,
  updateBoothStatus,
  deleteBooth,
  updateBoothIntro,
  issueBoothQr,
  getBoothQr,
};
