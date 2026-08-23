import { apiRequest } from "./apiClient.js";

export const funnelSessionApi = {
  summary: (eventId, date) => apiRequest(`/v1/admin/funnel-sessions/${eventId}/summary?date=${date}`),
  reconstruct: (eventId, date) =>
    apiRequest(`/v1/admin/funnel-sessions/${eventId}/reconstruct?date=${date}`, { method: "POST" }),
  ranking: (date) => apiRequest(`/v1/admin/funnel-sessions/summary?date=${date}`),
  summaryForOrganizer: (organizationId, eventId, date) =>
    apiRequest(`/v1/organizations/${organizationId}/events/${eventId}/funnel-sessions/summary?date=${date}`),
};
