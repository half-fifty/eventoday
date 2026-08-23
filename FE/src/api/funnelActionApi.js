import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

export const funnelActionApi = {
  collect: (payload) => apiRequest("/funnel-actions", json("POST", payload)),
};
