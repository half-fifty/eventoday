import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

export const exchangeCodeApi = {
  getMyExchangeCodes: (params = {}) =>
    apiRequest(`/members/me/exchange-codes?${new URLSearchParams(params)}`),
  validateExchangeCode: (code) =>
    apiRequest("/exchange-codes/validation", json("POST", { code })),
  redeemExchangeCode: (code) =>
    apiRequest("/exchange-codes/redemption", json("POST", { code })),
};
