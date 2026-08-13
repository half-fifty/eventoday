import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const organizationSignupApi = {
  adminList: (params = {}) =>
    apiRequest(`/admin/organization-signups?${new URLSearchParams(params)}`),
  adminDetail: (applicationId) =>
    apiRequest(`/admin/organization-signups/${applicationId}`),
  approve: (applicationId) =>
    apiRequest(`/admin/organization-signups/${applicationId}/approval`, json("POST")),
  reject: (applicationId, reason) =>
    apiRequest(
      `/admin/organization-signups/${applicationId}/rejection`,
      json("POST", { reason })
    ),
};
