import { apiRequest } from "./apiClient.js";

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

export {
  verifyBusiness,
};
