import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from "react";

import {
  getCurrentMember,
  logout as requestLogout,
} from "../api/authApi.js";

const AuthContext = createContext(null);

const AuthProvider = ({ children }) => {
  const [member, setMember] = useState(null);
  const [loading, setLoading] = useState(true);

  const refreshMember = useCallback(async () => {
    setLoading(true);

    try {
      const currentMember = await getCurrentMember();
      setMember(currentMember);
      return currentMember;
    } catch (error) {
      setMember(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshMember();
  }, [refreshMember]);

  const logout = useCallback(async () => {
    try {
      await requestLogout();
    } finally {
      setMember(null);
    }
  }, []);

  const value = useMemo(() => {
    return {
      member,
      loading,
      isAuthenticated: member !== null,
      refreshMember,
      logout,
    };
  }, [member, loading, refreshMember, logout]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

export {
  AuthContext,
  AuthProvider,
};
