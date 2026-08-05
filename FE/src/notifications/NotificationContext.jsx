import { createContext, useContext } from "react";

const NotificationContext = createContext(null);

const useNotifications = () => {
  const context = useContext(NotificationContext);

  if (context === null) {
    throw new Error(
      "useNotifications는 NotificationSseProvider 내부에서 사용해야 합니다."
    );
  }

  return context;
};

export {
  NotificationContext,
  useNotifications,
};
