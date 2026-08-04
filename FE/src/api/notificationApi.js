import { apiRequest } from "./apiClient.js";

const getNotifications = async ({ page = 0, size = 20 } = {}) => {
  const searchParams = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: "createdAt,desc",
  });
  const response = await apiRequest(`/notifications?${searchParams}`);
  return response.data;
};

const getUnreadCount = async () => {
  const response = await apiRequest("/notifications/unread-count");
  return response.data.count;
};

const markAsRead = async (notificationId) => {
  const response = await apiRequest(
    `/notifications/${notificationId}/read`,
    { method: "PATCH" }
  );
  return response.data;
};

export {
  getNotifications,
  getUnreadCount,
  markAsRead,
};
