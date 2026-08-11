import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

export const exchangeCodeApi = {
  getGuestOrderExchangeCodes: (orderNo, orderAccessToken) =>
    apiRequest(`/ticket-orders/${encodeURIComponent(orderNo)}/exchange-codes`, {
      headers: { "X-Order-Access-Token": orderAccessToken },
    }),
  redeemGuestOrderExchangeCode: (orderNo, exchangeCodeId, orderAccessToken) =>
    apiRequest(`/ticket-orders/${encodeURIComponent(orderNo)}/exchange-codes/${encodeURIComponent(exchangeCodeId)}/redemption`, {
      method: "POST",
      headers: { "X-Order-Access-Token": orderAccessToken },
    }),
  getMyExchangeCodes: (params = {}) =>
    apiRequest(`/members/me/exchange-codes?${new URLSearchParams(params)}`),
  validateExchangeCode: (code) =>
    apiRequest("/exchange-codes/validation", json("POST", { code })),
  redeemExchangeCode: (code) =>
    apiRequest("/exchange-codes/redemption", json("POST", { code })),
  createExchangeCodeRequest: (eventId, payload) =>
    apiRequest(`/events/${eventId}/exchange-code-requests`, json("POST", payload)),
  getEventExchangeCodeRequests: (eventId, params = {}) =>
    apiRequest(`/events/${eventId}/exchange-code-requests?${new URLSearchParams(params)}`),
  getExchangeCodeRequest: (requestId) =>
    apiRequest(`/exchange-code-requests/${requestId}`),
  getAdminExchangeCodeRequests: (params = {}) =>
    apiRequest(`/admin/exchange-code-requests?${new URLSearchParams(params)}`),
  approveExchangeCodeRequest: (requestId) =>
    apiRequest(`/admin/exchange-code-requests/${requestId}/approval`, { method: "POST" }),
  rejectExchangeCodeRequest: (requestId, reason) =>
    apiRequest(`/admin/exchange-code-requests/${requestId}/rejection`, json("POST", { reason })),
  issueExchangeCodes: (requestId) =>
    apiRequest(`/admin/exchange-code-requests/${requestId}/issuance`, { method: "POST" }),
  resendExchangeCodeEmail: (requestId) =>
    apiRequest(`/admin/exchange-code-requests/${requestId}/email-resend`, { method: "POST" }),
};
