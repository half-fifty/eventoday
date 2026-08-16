import { apiRequest } from "./apiClient.js";

const loginBusiness = async (loginData) => {
  const response = await apiRequest("/auth/business/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(loginData),
  });

  return response.data;
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

const buildBusinessFormData = (data, certificateFile) => {
  const formData = new FormData();
  formData.append(
    "data",
    new Blob([JSON.stringify(data)], { type: "application/json" })
  );
  if (certificateFile) {
    formData.append("certificateFile", certificateFile);
  }
  return formData;
};

const signupBusiness = async (signupData, certificateFile) => {
  const response = await apiRequest("/auth/business/signup", {
    method: "POST",
    body: buildBusinessFormData(signupData, certificateFile),
  });

  return response.data;
};

export {
  loginBusiness,
  signupBusiness,
  verifyBusiness,
};
