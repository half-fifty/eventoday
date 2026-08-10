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
};
