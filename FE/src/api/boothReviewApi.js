import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

const buildQuery = (params = {}) => {
  const query = new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([, value]) => value !== "" && value != null))
  );
  const queryString = query.toString();
  return queryString ? `?${queryString}` : "";
};

// 부스별 후기 목록 (페이징). ApiResponse 래핑 없이 Page를 그대로 반환한다.
const listReviews = async (boothId, params = {}) => {
  return apiRequest(`/booths/${boothId}/reviews${buildQuery(params)}`);
};

const createReview = async (boothId, payload) => {
  return apiRequest(`/booths/${boothId}/reviews`, json("POST", payload));
};

const updateReview = async (boothId, reviewId, payload) => {
  return apiRequest(`/booths/${boothId}/reviews/${reviewId}`, json("PUT", payload));
};

const deleteReview = async (boothId, reviewId) => {
  await apiRequest(`/booths/${boothId}/reviews/${reviewId}`, { method: "DELETE" });
};

// 내가 작성한 모든 부스 후기 목록 (행사 전체에 걸쳐 조회). ApiResponse 래핑 없음.
const getMyReviews = async (params = {}) => {
  return apiRequest(`/members/me/booth-reviews${buildQuery(params)}`);
};

export {
  listReviews,
  createReview,
  updateReview,
  deleteReview,
  getMyReviews,
};
