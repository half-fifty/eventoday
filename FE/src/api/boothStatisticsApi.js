import { apiRequest } from "./apiClient.js";

const getEventOverview = async (eventId, from, to) => {
  const query = new URLSearchParams({ from, to });
  const response = await apiRequest(
    `/events/${eventId}/statistics/overview?${query}`
  );
  return response.data;
};

export { getEventOverview };
