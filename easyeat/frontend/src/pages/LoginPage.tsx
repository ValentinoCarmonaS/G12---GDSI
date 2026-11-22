import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { LoginForm } from '../components/auth/LoginForm';
import { AuthCardShell } from '../components/diner';
import { FeedbackState } from '../components/primitives/FeedbackState';
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
  const stateReason = state?.reason || sessionStorage.getItem('session_expired_reason');
  const targetDeepLink = state?.from;

  React.useEffect(() => {
    if (sessionStorage.getItem('session_expired_reason')) {
      sessionStorage.removeItem('session_expired_reason');
    }
  }, []);

  const handleLogin = async (credentials: LoginCredentials) => {
    const data = await authApi.login(credentials);
    setToken(data.access_token);

    const claims = decodeClaims(data.access_token);
    const userRole = (claims?.role as Role) || 'COMENSAL';
    const mustChangePassword = claims?.must_change_password ?? false;

    // Resolve post-auth destination: preserve authorized deep link or fallback to role default
    const destination = resolvePostAuthRedirect(userRole, targetDeepLink, mustChangePassword);
    navigate(destination, { replace: true });
  };

  return (
    <AuthCardShell
      brandLabel="Acceso"
      title="EasyEat"
      subtitle="Iniciá sesión en tu cuenta para continuar"
    >
      {stateReason && (
        <div className="mb-5">
          <FeedbackState
            variant="warning"
            role="alert"
            icon={<AlertTriangle className="h-4 w-4" />}
            message={stateReason}
          />
        </div>
      )}

      <LoginForm onSubmit={handleLogin} />
    </AuthCardShell>
  );
};

export default LoginPage;
