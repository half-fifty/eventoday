import { Navigate, useLocation } from "react-router-dom";

import useAuth from "../hooks/useAuth.js";

const ProtectedRoute = ({ children }) => {
  const location = useLocation();
  const {
    loading,
    isAuthenticated,
  } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p>로그인 상태를 확인하고 있습니다.</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    const redirectPath =
      location.pathname + location.search;
    const loginPath =
      `/login?redirect=${encodeURIComponent(redirectPath)}`;

    return (
      <Navigate
        to={loginPath}
        replace
      />
    );
  }

  return children;
};

export default ProtectedRoute;
