import { API_BASE_URL, apiRequest } from "./apiClient.js";

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

// file.type이 BE 허용 목록과 다르면 확장자로 추론한 MIME 타입으로 재포장
const normalizeFile = (file) => {
  const ext = file.name.split(".").pop().toLowerCase();
  const expected = MIME_BY_EXT[ext];
  if (!expected || file.type === expected) return file;
  return new File([file], file.name, { type: expected });
};

const uploadFile = async (file, accessLevel = "PUBLIC") => {
  const formData = new FormData();
  formData.append("file", normalizeFile(file));
  formData.append("accessLevel", accessLevel);

  const response = await apiRequest("/v1/files", {
    method: "POST",
    body: formData,
  });
  return response.data;
};

const fileDownloadUrl = (fileId) => `${API_BASE_URL}/v1/files/${fileId}/download`;

export { uploadFile, fileDownloadUrl };
