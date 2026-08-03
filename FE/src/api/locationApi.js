import { apiRequest } from "./apiClient.js";

export const locationApi = {
  search: (query) => apiRequest(`/v1/locations/search?${new URLSearchParams({ query })}`),
  postalCode: (address) => apiRequest(`/v1/locations/postal-code?${new URLSearchParams({ address })}`),
};
