import { apiBlobRequest, apiRequest } from "./apiClient.js";

const pathId = (value) => encodeURIComponent(String(value));
const withQuery = (path, params = {}) => {
  const query = new URLSearchParams(params).toString();
  return query ? `${path}?${query}` : path;
};

export const admissionApi = {
  getMyAdmissionTickets: (params = {}) =>
    apiRequest(withQuery("/members/me/admission-tickets", params)),
  getAdmissionTicket: (admissionTicketId) =>
    apiRequest(`/members/me/admission-tickets/${pathId(admissionTicketId)}`),
  getAdmissionTicketQr: (admissionTicketId) =>
    apiBlobRequest(`/members/me/admission-tickets/${pathId(admissionTicketId)}/qr`, {
      headers: { Accept: "image/png" },
    }),
  checkIn: (eventId, payload) =>
    apiRequest(`/events/${pathId(eventId)}/admission-checkins`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    }),
  cancelCheckIn: (eventId, admissionTicketId) =>
    apiRequest(`/events/${pathId(eventId)}/admission-tickets/${pathId(admissionTicketId)}/check-in-cancellation`, {
      method: "POST",
    }),
  getEventAdmissionTickets: (eventId, params = {}) =>
    apiRequest(withQuery(`/events/${pathId(eventId)}/admission-tickets`, params)),
  getAdmissionLogs: (eventId, params = {}) =>
    apiRequest(withQuery(`/events/${pathId(eventId)}/admission-logs`, params)),
};
