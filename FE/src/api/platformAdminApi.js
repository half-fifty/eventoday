import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const platformAdminApi = {
  dashboard: () => apiRequest("/v1/admin/platform/dashboard"),
  accounts: () => apiRequest("/v1/admin/platform/accounts"),
  changeAccountStatus: (memberId, status) =>
    apiRequest(`/v1/admin/platform/accounts/${memberId}/status`, json("PATCH", { status })),
  statistics: () => apiRequest("/v1/admin/platform/statistics"),
  audit: () => apiRequest("/v1/admin/platform/audit"),
};
