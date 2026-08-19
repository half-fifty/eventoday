import { apiRequest } from "./apiClient.js";

const pathId = (value) => encodeURIComponent(String(value));

const json = (method, body, headers = {}) => ({
  method,
  headers: {
    "Content-Type": "application/json",
    ...headers,
  },
  body: body === undefined ? undefined : JSON.stringify(body),
});

export const aiApi = {
  askEventCopilot: (eventId, request) =>
    apiRequest(`/events/${pathId(eventId)}/ai/copilot`, json("POST", request)),

  explainRefundFailure: (paymentId, request, orderAccessToken = null) =>
    apiRequest(`/payments/${pathId(paymentId)}/refunds/ai-explanation`, json(
      "POST",
      request,
      orderAccessToken ? { "X-Order-Access-Token": orderAccessToken } : {}
    )),

  explainMyAdmissionFailure: (admissionTicketId, request) =>
    apiRequest(`/members/me/admission-tickets/${pathId(admissionTicketId)}/ai-failure-explanation`, json("POST", request)),

  explainGuestAdmissionFailure: (orderNo, admissionTicketId, orderAccessToken, request) =>
    apiRequest(`/ticket-orders/${pathId(orderNo)}/admission-tickets/${pathId(admissionTicketId)}/ai-failure-explanation`, json(
      "POST",
      request,
      { "X-Order-Access-Token": orderAccessToken }
    )),
};
