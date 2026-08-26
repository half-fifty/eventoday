import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

const queryString = (params = {}) => {
  const query = new URLSearchParams(
    Object.fromEntries(
      Object.entries(params).filter(([, value]) => value !== "" && value != null)
    )
  );
  const value = query.toString();
  return value ? `?${value}` : "";
};

// 참가 기업: 부스 신청 제출
const submitApplication = async (recruitmentId, payload) => {
  const response = await apiRequest(
    `/booth-recruitments/${recruitmentId}/applications`,
    json("POST", payload)
  );
  return response.data;
};

// 참가 기업: 조직의 신청 목록
const getOrganizationApplications = async (organizationId) => {
  const response = await apiRequest(
    `/organizations/${organizationId}/booth-applications`
  );
  return response.data;
};

const cancelApplication = async (applicationId) => {
  await apiRequest(`/booth-applications/${applicationId}/cancellation`, {
    method: "POST",
  });
};

// 행사 관리자: 행사별 신청 목록 및 검색
const getEventApplications = async (eventId, params = {}) => {
  const response = await apiRequest(
    `/events/${eventId}/booth-applications${queryString(params)}`
  );
  return response.data;
};

const getApplicationDetail = async (applicationId) => {
  const response = await apiRequest(`/booth-applications/${applicationId}`);
  return response.data;
};

const getApplicationFiles = async (applicationId) => {
  const response = await apiRequest(`/booth-applications/${applicationId}/files`);
  return response.data;
};

const startApplicationReview = async (applicationId) => {
  await apiRequest(`/booth-applications/${applicationId}/review-start`, {
    method: "POST",
  });
};

const approveApplication = async (applicationId) => {
  await apiRequest(`/booth-applications/${applicationId}/approval`, {
    method: "POST",
  });
};

const rejectApplication = async (applicationId, rejectionReason) => {
  await apiRequest(
    `/booth-applications/${applicationId}/rejection`,
    json("POST", { rejectionReason })
  );
};

// 기존 화면에서 사용하는 이름을 유지해 rebase 후에도 양쪽 기능이 동작하게 한다.
const listMyApplications = getOrganizationApplications;
const listEventApplications = getEventApplications;
const getApplication = getApplicationDetail;
const listApplicationFiles = getApplicationFiles;
const startReview = startApplicationReview;

export {
  submitApplication,
  getOrganizationApplications,
  listMyApplications,
  cancelApplication,
  getEventApplications,
  listEventApplications,
  getApplicationDetail,
  getApplication,
  getApplicationFiles,
  listApplicationFiles,
  startApplicationReview,
  startReview,
  approveApplication,
  rejectApplication,
};
