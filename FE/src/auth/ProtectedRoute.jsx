import { Navigate, useLocation } from "react-router-dom";

import useAuth from "../hooks/useAuth.js";

const ProtectedRoute = ({ children, roles = null, organizationTypes = null }) => {
  const location = useLocation();
  const {
    loading,
    isAuthenticated,
    member,
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
      location.pathname + location.search + location.hash;
    const loginPath =
      `/login?redirect=${encodeURIComponent(redirectPath)}`;

    return (
      <Navigate
        to={loginPath}
        replace
      />
    );
  }

  if (
    Array.isArray(roles) &&
    !roles.includes(member.platformRole)
  ) {
    return <Navigate to="/" replace />;
  }

  if (
    Array.isArray(organizationTypes) &&
    !organizationTypes.includes(member.organization?.organizationType)
  ) {
    return <Navigate to="/" replace />;
  }

  return children;
};

export default ProtectedRoute;
