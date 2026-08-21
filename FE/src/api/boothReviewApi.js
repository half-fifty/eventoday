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

// 부스 후기 코멘트 AI 요약. ApiResponse 래핑 없이 결과를 그대로 반환한다.
const getReviewSummary = async (boothId) => {
  return apiRequest(`/booths/${boothId}/reviews/summary`);
};

// 부스 후기 키워드 검색
const searchReviews = async (boothId, keyword, params = {}) => {
  return apiRequest(`/booths/${boothId}/reviews/search${buildQuery({ ...params, keyword })}`);
};

// 리뷰 신고. reasonCode: SPAM|ABUSE|HARASSMENT|FALSE_INFORMATION|OTHER, reason은 OTHER일 때만 필수
const reportReview = async (boothId, reviewId, payload) => {
  return apiRequest(`/booths/${boothId}/reviews/${reviewId}/reports`, json("POST", payload));
};

// 신고 취소 (본인이 넣은 신고만 철회 가능)
const cancelReport = async (boothId, reviewId) => {
  await apiRequest(`/booths/${boothId}/reviews/${reviewId}/reports`, { method: "DELETE" });
};

export {
  listReviews,
  createReview,
  updateReview,
  deleteReview,
  getMyReviews,
  getReviewSummary,
  searchReviews,
  reportReview,
  cancelReport,
};
