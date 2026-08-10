import { apiRequest } from "./apiClient.js";

// 확장자별 MIME 타입 매핑 (BE FileService ALLOWED_MIME_TYPES 기준)
// OS/브라우저에 따라 file.type이 비어있거나 다르게 인식되면 BE MIME 검사에서
// 거부되므로, 확장자 기반으로 MIME 타입을 명시해서 전송한다.
const MIME_BY_EXT = {
  pdf: "application/pdf",
  doc: "application/msword",
  docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  gif: "image/gif",
  webp: "image/webp",
};

// file.type이 BE 허용 목록에 없으면 확장자로 추론한 MIME 타입으로 재포장
const normalizeFile = (file) => {
  const ext = file.name.split(".").pop().toLowerCase();
  const expected = MIME_BY_EXT[ext];
  if (!expected || file.type === expected) return file;
  return new File([file], file.name, { type: expected });
};

// BE EventContentController가 multipart/form-data를 받으므로
// data 파트는 JSON Blob, file 파트는 선택 첨부로 구성한다.
const buildContentForm = (data, file) => {
  const formData = new FormData();
  formData.append("data", new Blob([JSON.stringify(data)], { type: "application/json" }));
  if (file) formData.append("file", normalizeFile(file));
  return formData;
};

// CONTENT-API-001: 공지·자료 목록 (contentType: "NOTICE" | "RESOURCE" | 생략 시 전체)
const listContents = async (eventId, contentType) => {
  const query = contentType ? `?contentType=${contentType}` : "";
  const response = await apiRequest(`/events/${eventId}/contents${query}`);
  return response.data;
};

// CONTENT-API-006: 전체 공지·자료 목록 (공개 행사 대상, 페이지네이션)
// params: { contentType, page, size }  ※ size 최대 100
// 응답: { content: [{ eventName, content: {...} }], page, size, totalElements, totalPages, first, last, empty }
const listAllContents = async (params = {}) => {
  const query = new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([, value]) => value !== "" && value != null))
  );
  const queryString = query.toString();
  const response = await apiRequest(`/contents${queryString ? `?${queryString}` : ""}`);
  return response.data;
};

// CONTENT-API-002: 공지·자료 상세
const getContent = async (contentId) => {
  const response = await apiRequest(`/event-contents/${contentId}`);
  return response.data;
};

// CONTENT-API-003: 공지·자료 등록 (EVENT_MANAGER)
// data: { contentType, resourceType, audience, title, content, version, pinned }
const createContent = async (eventId, data, file) => {
  const response = await apiRequest(`/events/${eventId}/contents`, {
    method: "POST",
    body: buildContentForm(data, file),
  });
  return response.data;
};

// CONTENT-API-004: 공지·자료 수정 (EVENT_MANAGER)
const updateContent = async (contentId, data, file) => {
  const response = await apiRequest(`/event-contents/${contentId}`, {
    method: "PATCH",
    body: buildContentForm(data, file),
  });
  return response.data;
};

// CONTENT-API-005: 공지·자료 삭제 (EVENT_MANAGER)
const deleteContent = async (contentId) => {
  await apiRequest(`/event-contents/${contentId}`, { method: "DELETE" });
};

export { listContents, listAllContents, getContent, createContent, updateContent, deleteContent };
