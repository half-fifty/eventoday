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
};
