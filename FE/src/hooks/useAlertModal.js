import { useContext } from "react";

import { AlertModalContext } from "../feedback/AlertModalProvider.jsx";

const useAlertModal = () => {
  const context = useContext(AlertModalContext);

  if (context === null) {
    throw new Error("useAlertModal은 AlertModalProvider 내부에서 사용해야 합니다.");
  }

  return context;
};

export default useAlertModal;
