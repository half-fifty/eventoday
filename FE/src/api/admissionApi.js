import { apiBlobRequest, apiRequest } from "./apiClient.js";

export const admissionApi = {
  getMyAdmissionTickets: (params = {}) =>
    apiRequest(`/members/me/admission-tickets?${new URLSearchParams(params)}`),
  getAdmissionTicket: (admissionTicketId) =>
    apiRequest(`/members/me/admission-tickets/${admissionTicketId}`),
  getAdmissionTicketQr: (admissionTicketId) =>
    apiBlobRequest(`/members/me/admission-tickets/${admissionTicketId}/qr`, {
      headers: { Accept: "image/png" },
    }),
  checkIn: (eventId, payload) =>
    apiRequest(`/events/${eventId}/admission-checkins`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    }),
  cancelCheckIn: (eventId, admissionTicketId) =>
    apiRequest(`/events/${eventId}/admission-tickets/${admissionTicketId}/check-in-cancellation`, {
      method: "POST",
    }),
  getEventAdmissionTickets: (eventId, params = {}) =>
    apiRequest(`/events/${eventId}/admission-tickets?${new URLSearchParams(params)}`),
  getAdmissionLogs: (eventId, params = {}) =>
    apiRequest(`/events/${eventId}/admission-logs?${new URLSearchParams(params)}`),
};
