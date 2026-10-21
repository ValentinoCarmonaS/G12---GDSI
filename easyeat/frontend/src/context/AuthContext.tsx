import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { AuthenticatedUser, JwtClaims, Role } from '../types/auth';

interface AuthContextType {
  user: AuthenticatedUser | null;
  token: string | null;
  isAuthenticated: boolean;
  setToken: (token: string) => void;
  clearAuth: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

function decodeJwtPayload(token: string): JwtClaims | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload) as JwtClaims;
  } catch {
    return null;
  }
}

function parseUserFromToken(token: string): AuthenticatedUser | null {
  const claims = decodeJwtPayload(token);
  if (!claims || !claims.sub) return null;

  // Optional: check expiration timestamp
  if (claims.exp && claims.exp * 1000 < Date.now()) {
    return null;
  }

  return {
    id: claims.sub,
    email: claims.email || '',
    role: (claims.role as Role) || 'COMENSAL',
    tenantId: claims.tenant_id || null,
    tokenVersion: claims.token_version ?? 0,
  };
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setTokenState] = useState<string | null>(() => {
    return sessionStorage.getItem('access_token');
  });

  const [user, setUser] = useState<AuthenticatedUser | null>(() => {
    const stored = sessionStorage.getItem('access_token');
    return stored ? parseUserFromToken(stored) : null;
  });

  const clearAuth = useCallback(() => {
    sessionStorage.removeItem('access_token');
    setTokenState(null);
    setUser(null);
  }, []);

  const setToken = useCallback((newToken: string) => {
    const parsed = parseUserFromToken(newToken);
    if (parsed) {
      sessionStorage.setItem('access_token', newToken);
      setTokenState(newToken);
      setUser(parsed);
    } else {
      clearAuth();
    }
  }, [clearAuth]);

  useEffect(() => {
    const handleStorageChange = () => {
      const current = sessionStorage.getItem('access_token');
      if (current !== token) {
        if (current) {
          const parsed = parseUserFromToken(current);
          setTokenState(current);
          setUser(parsed);
        } else {
          setTokenState(null);
          setUser(null);
        }
      }
    };

    window.addEventListener('storage', handleStorageChange);
    window.addEventListener('easyeat:session-expired', clearAuth);
    return () => {
      window.removeEventListener('storage', handleStorageChange);
      window.removeEventListener('easyeat:session-expired', clearAuth);
    };
  }, [token, clearAuth]);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!user && !!token,
        setToken,
        clearAuth,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

export default AuthContext;
