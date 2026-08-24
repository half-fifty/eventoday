import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

export const eventApi = {
  managedOrganizations: () => apiRequest("/v1/me/managed-organizations"),
  admissionEvents: () => apiRequest("/me/admission-events"),
  list: (params = {}) =>
    apiRequest(`/v1/events?${new URLSearchParams(params)}`),
  detail: (eventId) => apiRequest(`/v1/events/${eventId}`),
  suggestContent: (payload) => apiRequest("/v1/events/content-suggestions", json("POST", payload)),
  detailImages: (eventId) => apiRequest(`/v1/events/${eventId}/detail-images`),
  exhibitCategories: () => apiRequest("/v1/exhibit-categories"),
  extractPoster: (organizationId, image) => {
    const body = new FormData();
    body.append("image", image);
    return apiRequest(
      `/v1/organizations/${organizationId}/events/poster-extraction`,
      { method: "POST", body },
    );
  },
  createTicketOrder: (eventId, payload, idempotencyKey, requestOptions = {}) => {
    const options = json("POST", payload);
    return apiRequest(`/events/${eventId}/ticket-orders`, {
      ...options,
      ...requestOptions,
      headers: {
        ...options.headers,
        ...(requestOptions.headers || {}),
        "Idempotency-Key": idempotencyKey,
      },
    });
  },
  coexParkingStatus: () =>
    apiRequest("/v1/venue-guides/coex/parking-status"),
  organizationList: (organizationId, params = {}) =>
    apiRequest(
      `/v1/organizations/${organizationId}/events?${new URLSearchParams(params)}`,
    ),
  managedDetail: (organizationId, eventId) =>
    apiRequest(`/v1/organizations/${organizationId}/events/${eventId}`),
  managedDetailImages: (organizationId, eventId) =>
    apiRequest(
      `/v1/organizations/${organizationId}/events/${eventId}/detail-images`,
    ),
  replaceDetailImages: (organizationId, eventId, images) =>
    apiRequest(
      `/v1/organizations/${organizationId}/events/${eventId}/detail-images`,
      json("PUT", { images }),
    ),
  create: (organizationId, payload) =>
    apiRequest(
      `/v1/organizations/${organizationId}/events`,
      json("POST", payload),
    ),
  update: (organizationId, eventId, payload) =>
    apiRequest(
      `/v1/organizations/${organizationId}/events/${eventId}`,
      json("PATCH", payload),
    ),
  updatePoster: (organizationId, eventId, representativeFileId) =>
    apiRequest(
      `/v1/organizations/${organizationId}/events/${eventId}/poster`,
      json("PATCH", { representativeFileId }),
    ),
  updatePublicInfo: (organizationId, eventId, payload) =>
    apiRequest(
      `/v1/organizations/${organizationId}/events/${eventId}/public-info`,
      json("PATCH", payload),
    ),
  submit: (eventId) =>
    apiRequest(`/v1/events/${eventId}/submission`, json("POST")),
  publish: (eventId) =>
    apiRequest(`/v1/events/${eventId}/publication`, json("POST")),
  cancel: (eventId) =>
    apiRequest(`/v1/events/${eventId}/cancellation`, json("POST")),
  adminList: (params = {}) =>
    apiRequest(`/v1/admin/events?${new URLSearchParams(params)}`),
  adminDetail: (eventId) => apiRequest(`/v1/admin/events/${eventId}`),
  approve: (eventId) =>
    apiRequest(`/v1/admin/events/${eventId}/approval`, json("POST")),
  reject: (eventId, reason) =>
    apiRequest(
      `/v1/admin/events/${eventId}/rejection`,
      json("POST", { reason }),
    ),
  suspend: (eventId) =>
    apiRequest(`/v1/admin/events/${eventId}/suspension`, json("POST")),
  members: (eventId) => apiRequest(`/v1/events/${eventId}/members`),
  addMember: (eventId, payload) =>
    apiRequest(`/v1/events/${eventId}/members`, json("POST", payload)),
  updateMember: (eventId, memberId, payload) =>
    apiRequest(
      `/v1/events/${eventId}/members/${memberId}`,
      json("PATCH", payload),
    ),
  removeMember: (eventId, memberId) =>
    apiRequest(`/v1/events/${eventId}/members/${memberId}`, {
      method: "DELETE",
    }),
};
