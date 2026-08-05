import {
  API_BASE_URL,
  apiRequest,
} from "./apiClient.js";

const loginWithGoogle = () => {
  window.location.href =
    `${API_BASE_URL}/oauth2/authorization/google`;
};

const loginWithNaver = () => {
  window.location.href =
    `${API_BASE_URL}/oauth2/authorization/naver`;
};

const getCurrentMember = async () => {
  const response = await apiRequest("/auth/me");
  return response.data;
};

const reissueTokens = async () => {
  return apiRequest("/auth/reissue", {
    method: "POST",
  });
};

const logout = async () => {
  return apiRequest("/auth/logout", {
    method: "POST",
  });
};

export {
  loginWithGoogle,
  loginWithNaver,
  getCurrentMember,
  reissueTokens,
  logout,
};
