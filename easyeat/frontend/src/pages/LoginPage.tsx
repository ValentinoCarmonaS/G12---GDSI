import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { LoginForm } from '../components/auth/LoginForm';
import { authApi } from '../api/authApi';
import { useAuth } from '../hooks/useAuth';
import { resolvePostAuthRedirect } from '../router/postAuthRouting';
import { LoginCredentials, JwtClaims, Role } from '../types/auth';

function decodeClaims(token: string): JwtClaims | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(json) as JwtClaims;
  } catch {
    return null;
  }
}

export const LoginPage: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { setToken } = useAuth();

  const state = location.state as { from?: string; reason?: string } | undefined;
  const stateReason = state?.reason;
  const targetDeepLink = state?.from;

  const handleLogin = async (credentials: LoginCredentials) => {
    const data = await authApi.login(credentials);
    setToken(data.access_token);

    const claims = decodeClaims(data.access_token);
    const userRole = (claims?.role as Role) || 'COMENSAL';

    // Resolve post-auth destination: preserve authorized deep link or fallback to role default
    const destination = resolvePostAuthRedirect(userRole, targetDeepLink);
    navigate(destination, { replace: true });
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="text-center">
          <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight">EasyEat</h2>
        </div>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10 border border-gray-100">
          {stateReason && (
            <div
              role="alert"
              className="mb-6 p-3 text-sm rounded-md bg-amber-50 border border-amber-200 text-amber-800"
            >
              {stateReason}
            </div>
          )}

          <LoginForm onSubmit={handleLogin} />
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
