import { apiRequest } from "./apiClient.js";

const listPublicRecruitments = async (status) => {
  const query = status ? `?status=${status}` : "";
  const response = await apiRequest(`/booth-recruitments${query}`);
  return response.data;
};

const getPublicRecruitment = async (recruitmentId) => {
  const response = await apiRequest(`/booth-recruitments/${recruitmentId}`);
  return response.data;
};

const getManagementRecruitment = async (eventId) => {
  const response = await apiRequest(`/events/${eventId}/booth-recruitment/management`);
  return response.data;
};

const createRecruitment = async (eventId, payload) => {
  const response = await apiRequest(`/events/${eventId}/booth-recruitment`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return response.data;
};

const updateRecruitment = async (eventId, payload) => {
  const response = await apiRequest(`/events/${eventId}/booth-recruitment`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return response.data;
};

const closeRecruitment = async (eventId) => {
  const response = await apiRequest(`/events/${eventId}/booth-recruitment/closure`, {
    method: "POST",
  });
  return response.data;
};

const completeRecruitment = async (eventId) => {
  const response = await apiRequest(`/events/${eventId}/booth-recruitment/completion`, {
    method: "POST",
  });
  return response.data;
};

export {
  listPublicRecruitments,
  getPublicRecruitment,
  getManagementRecruitment,
  createRecruitment,
  updateRecruitment,
  closeRecruitment,
  completeRecruitment,
};
