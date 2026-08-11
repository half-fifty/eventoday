import { apiRequest } from "./apiClient.js";

// boothApi.js와 동일한 JSON 요청 헬퍼 패턴
const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

// APP-API-001: 부스 신청 제출 (ORG_MEMBER)
const submitApplication = async (recruitmentId, payload) => {
  const response = await apiRequest(
    `/booth-recruitments/${recruitmentId}/applications`,
    json("POST", payload)
  );
  return response.data;
};

// APP-API-002: 참가기업 신청 목록 (ORG_MEMBER)
const listMyApplications = async (organizationId) => {
  const response = await apiRequest(`/organizations/${organizationId}/booth-applications`);
  return response.data;
};

// APP-API-003: 신청 상세 (ORG_MEMBER / EVENT_MANAGER)
const getApplication = async (applicationId) => {
  const response = await apiRequest(`/booth-applications/${applicationId}`);
  return response.data;
};

// APP-API-004: 신청 취소 (ORG_MEMBER)
const cancelApplication = async (applicationId) => {
  await apiRequest(`/booth-applications/${applicationId}/cancellation`, { method: "POST" });
};

// APP-API-005: 행사 신청 목록·검색 (EVENT_MANAGER)
// params: { status, teamName, boothCode, submittedFrom, submittedTo, page, size }
// 응답: { content, page, size, totalElements, totalPages, first, last, empty }
const listEventApplications = async (eventId, params = {}) => {
  const query = new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([, value]) => value !== "" && value != null))
  );
  const queryString = query.toString();
  const response = await apiRequest(
    `/events/${eventId}/booth-applications${queryString ? `?${queryString}` : ""}`
  );
  return response.data;
};

// APP-API-006: 검토 시작 (EVENT_MANAGER)
const startReview = async (applicationId) => {
  await apiRequest(`/booth-applications/${applicationId}/review-start`, { method: "POST" });
};

// APP-API-007: 신청 승인·부스 배정 (EVENT_MANAGER)
const approveApplication = async (applicationId) => {
  await apiRequest(`/booth-applications/${applicationId}/approval`, { method: "POST" });
};

// APP-API-008: 신청 반려·부스 복원 (EVENT_MANAGER) - rejectionReason 필수
const rejectApplication = async (applicationId, rejectionReason) => {
  await apiRequest(
    `/booth-applications/${applicationId}/rejection`,
    json("POST", { rejectionReason })
  );
};

// APP-API-009: 신청 첨부파일 목록 (ORG_MEMBER / EVENT_MANAGER)
// 응답 항목: { fileId, fileType(ESTIMATE|OTHER), originalName, mimeType, fileSize, downloadUrl }
const listApplicationFiles = async (applicationId) => {
  const response = await apiRequest(`/booth-applications/${applicationId}/files`);
  return response.data;
};

export {
  submitApplication,
  listMyApplications,
  getApplication,
  cancelApplication,
  listEventApplications,
  startReview,
  approveApplication,
  rejectApplication,
  listApplicationFiles,
};
