import { apiRequest } from "./apiClient.js";

export const paymentApi = {
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
  getTicketOrder: (orderNo, orderAccessToken = null) => apiRequest(`/ticket-orders/${orderNo}`, {
    headers: {
      ...(orderAccessToken ? { "X-Order-Access-Token": orderAccessToken } : {}),
    },
  }),
  getPayment: (paymentId, orderAccessToken = null) => apiRequest(`/payments/${paymentId}`, {
    headers: {
      ...(orderAccessToken ? { "X-Order-Access-Token": orderAccessToken } : {}),
    },
  }),
};
