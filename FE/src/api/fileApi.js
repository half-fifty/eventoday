import { API_BASE_URL, apiRequest } from "./apiClient.js";

const uploadFile = async (file, accessLevel = "PUBLIC") => {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("accessLevel", accessLevel);

  const response = await apiRequest("/v1/files", {
    method: "POST",
    body: formData,
  });
  return response.data;
};

const fileDownloadUrl = (fileId) => `${API_BASE_URL}/v1/files/${fileId}/download`;

export { uploadFile, fileDownloadUrl };
