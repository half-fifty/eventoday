import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

export const advertisementApi = {
  active: (params = {}) => apiRequest(`/v1/advertisements/active?${new URLSearchParams(params)}`),
  pricing: () => apiRequest("/v1/advertisements/pricing"),
  createEvent: (eventId, payload) =>
    apiRequest(`/v1/events/${eventId}/advertisements`, json("POST", payload)),
  createBooth: (boothId, payload) =>
    apiRequest(`/v1/booths/${boothId}/advertisements`, json("POST", payload)),
  organizationList: (organizationId, params = {}) =>
    apiRequest(`/v1/organizations/${organizationId}/advertisements?${new URLSearchParams(params)}`),
  detail: (advertisementId) => apiRequest(`/v1/advertisements/${advertisementId}`),
  update: (advertisementId, payload) =>
    apiRequest(`/v1/advertisements/${advertisementId}`, json("PATCH", payload)),
  updateCreative: (advertisementId, payload) =>
    apiRequest(`/v1/advertisements/${advertisementId}/creative`, json("PATCH", payload)),
  selectPaymentMethod: (advertisementId, paymentMethod) =>
    apiRequest(`/v1/advertisements/${advertisementId}/payment-method`, json("PATCH", { paymentMethod })),
  cancel: (advertisementId) =>
    apiRequest(`/v1/advertisements/${advertisementId}/cancellation`, json("POST")),
  archive: (advertisementId) =>
    apiRequest(`/v1/advertisements/${advertisementId}/archive`, json("POST")),
  adminList: (params = {}) =>
    apiRequest(`/v1/admin/advertisements?${new URLSearchParams(params)}`),
  approve: (advertisementId) =>
    apiRequest(`/v1/admin/advertisements/${advertisementId}/approval`, json("POST")),
  reject: (advertisementId, reason) =>
    apiRequest(`/v1/admin/advertisements/${advertisementId}/rejection`, json("POST", { reason })),
  boothCandidates: (eventId) => apiRequest(`/v1/events/${eventId}/booth-ad-candidates`),
  suggestCopy: (payload) =>
    apiRequest("/v1/advertisements/copy-suggestions", json("POST", payload)),
};
