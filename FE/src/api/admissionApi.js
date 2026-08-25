import { apiBlobRequest, apiRequest } from "./apiClient.js";

const pathId = (value) => encodeURIComponent(String(value));
const withQuery = (path, params = {}) => {
  const query = new URLSearchParams(params).toString();
  return query ? `${path}?${query}` : path;
};

export const admissionApi = {
  getGuestOrderAdmissionTickets: (orderNo, orderAccessToken) =>
    apiRequest(`/ticket-orders/${pathId(orderNo)}/admission-tickets`, {
      headers: { "X-Order-Access-Token": orderAccessToken },
    }),
  getGuestOrderAdmissionTicket: (orderNo, admissionTicketId, orderAccessToken) =>
    apiRequest(`/ticket-orders/${pathId(orderNo)}/admission-tickets/${pathId(admissionTicketId)}`, {
      headers: { "X-Order-Access-Token": orderAccessToken },
    }),
  getGuestOrderAdmissionTicketQr: (orderNo, admissionTicketId, orderAccessToken) =>
    apiBlobRequest(`/ticket-orders/${pathId(orderNo)}/admission-tickets/${pathId(admissionTicketId)}/qr`, {
      headers: {
        Accept: "image/png",
        "X-Order-Access-Token": orderAccessToken,
      },
    }),
  getMyAdmissionTickets: (params = {}) =>
    apiRequest(withQuery("/members/me/admission-tickets", params)),
  // 온고잉 페이지 진입 시 이 행사의 입장권을 가진 회원인지 확인하는 용도.
  hasEventAdmission: (eventId) =>
    apiRequest(`/events/${pathId(eventId)}/admission-tickets/me`),
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
