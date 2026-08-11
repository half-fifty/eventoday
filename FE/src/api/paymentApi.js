import { apiRequest } from "./apiClient.js";

export const paymentApi = {
  recoverGuestOrderAccess: (orderNo, payload) => apiRequest(`/ticket-orders/${encodeURIComponent(orderNo)}/access-token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  }),
  confirm: (payload, orderAccessToken = null) => apiRequest("/payments/confirm", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(orderAccessToken ? { "X-Order-Access-Token": orderAccessToken } : {}),
    },
    body: JSON.stringify(payload),
  }),
  getMyTicketOrders: (params = {}) =>
    apiRequest(`/members/me/ticket-orders?${new URLSearchParams(params)}`),
  getTicketOrder: (orderNo, orderAccessToken = null) => apiRequest(`/ticket-orders/${encodeURIComponent(orderNo)}`, {
    headers: {
      ...(orderAccessToken ? { "X-Order-Access-Token": orderAccessToken } : {}),
    },
  }),
  getPayment: (paymentId, orderAccessToken = null) => apiRequest(`/payments/${encodeURIComponent(paymentId)}`, {
    headers: {
      ...(orderAccessToken ? { "X-Order-Access-Token": orderAccessToken } : {}),
    },
  }),
  requestRefund: (paymentId, payload, orderAccessToken = null) => apiRequest(`/payments/${encodeURIComponent(paymentId)}/refunds`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(orderAccessToken ? { "X-Order-Access-Token": orderAccessToken } : {}),
    },
    body: JSON.stringify(payload),
  }),
  getMyRefunds: (params = {}) =>
    apiRequest(`/members/me/refunds?${new URLSearchParams(params)}`),
  getRefund: (refundId, orderAccessToken = null) => apiRequest(`/refunds/${encodeURIComponent(refundId)}`, {
    headers: {
      ...(orderAccessToken ? { "X-Order-Access-Token": orderAccessToken } : {}),
    },
  }),
};
