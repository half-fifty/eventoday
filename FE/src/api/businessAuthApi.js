import { apiRequest } from "./apiClient.js";

const loginBusiness = async (loginData) => {
  return apiRequest("/auth/business/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(loginData),
  });
};

const verifyBusiness = async (verificationData) => {
  const response = await apiRequest("/auth/business/verify", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(verificationData),
  });

  return response.data;
};

const signupBusiness = async (signupData) => {
  const response = await apiRequest("/auth/business/signup", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(signupData),
  });

  return response.data;
};

export {
  loginBusiness,
  signupBusiness,
  verifyBusiness,
};
