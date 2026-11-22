import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { useAuth } from '../hooks/useAuth';
import { getRoleDefaultRoute } from '../router/postAuthRouting';
import { AuthCardShell } from '../components/diner/AuthCardShell';
import { Button } from '../components/ui/Button';

export const FirstLoginPasswordPage: React.FC = () => {
  const navigate = useNavigate();
  const { user, setToken, clearAuth } = useAuth();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{
    currentPassword?: string;
    newPassword?: string;
    confirmNewPassword?: string;
  }>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // Password complexity checks (FR-003, FR-030)
  const hasMinLength = newPassword.length >= 8;
  const hasUppercase = /[A-Z]/.test(newPassword);
  const hasLowercase = /[a-z]/.test(newPassword);
  const hasNumber = /[0-9]/.test(newPassword);
  const isComplexityValid = hasMinLength && hasUppercase && hasLowercase && hasNumber;

  const validate = (): boolean => {
    const errors: {
      currentPassword?: string;
      newPassword?: string;
      confirmNewPassword?: string;
    } = {};

    if (!currentPassword) {
      errors.currentPassword = 'La contraseña actual es obligatoria';
    }

    if (!newPassword) {
      errors.newPassword = 'La nueva contraseña es obligatoria';
    } else if (!isComplexityValid) {
      errors.newPassword = 'La contraseña no cumple con los requisitos mínimos de seguridad';
    }

    if (!confirmNewPassword) {
      errors.confirmNewPassword = 'Debés confirmar la nueva contraseña';
    } else if (newPassword && confirmNewPassword && newPassword !== confirmNewPassword) {
      errors.confirmNewPassword = 'Las contraseñas no coinciden';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isLoading) return;

    setGeneralError(null);
    if (!validate()) return;

    setIsLoading(true);
    try {
      await authApi.changeFirstLoginPassword({
        currentPassword,
        newPassword,
        confirmNewPassword,
      });

      if (user?.email) {
        try {
          const loginResponse = await authApi.login({
            email: user.email,
            password: newPassword,
          });
          setToken(loginResponse.access_token);
          const destination = getRoleDefaultRoute(user.role);
          navigate(destination, { replace: true });
          return;
        } catch {
          // If automatic login refresh fails, clear auth and redirect to /login
        }
      }

      clearAuth();
      navigate('/login', { replace: true });
    } catch (err: unknown) {
      const error = err as {
        response?: { status?: number; data?: { detail?: string; message?: string } };
      };
      const detail = error.response?.data?.detail || error.response?.data?.message;
      if (detail) {
        setGeneralError(detail);
      } else {
        setGeneralError('Error al cambiar la contraseña. Intente nuevamente.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthCardShell
      brandLabel="EasyEat"
      title="Cambio Obligatorio de Contraseña"
      subtitle="Por motivos de seguridad, debés actualizar tu contraseña temporal antes de comenzar a operar."
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        {generalError && (
          <div
            role="alert"
            className="p-3 text-sm rounded-[var(--ee-radius-md)] bg-[var(--ee-danger-bg)] border border-[var(--ee-danger-border)] text-[var(--ee-danger-text)]"
          >
            {generalError}
          </div>
        )}

        <div>
          <label htmlFor="currentPassword" className="block text-sm font-medium text-[var(--ee-text)] mb-1">
            Contraseña actual (temporal)
          </label>
          <input
            id="currentPassword"
            type="password"
            name="currentPassword"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => {
              setCurrentPassword(e.target.value);
              if (fieldErrors.currentPassword) {
                setFieldErrors((prev) => ({ ...prev, currentPassword: undefined }));
              }
            }}
            disabled={isLoading}
            placeholder="••••••••••••"
            className={`w-full min-h-[44px] px-3 py-2 border rounded-[var(--ee-radius-sm)] bg-[var(--ee-surface)] text-[var(--ee-text)] shadow-sm placeholder:text-[var(--ee-text-muted)] focus-visible:outline-none focus-visible:ring-2 transition-colors ${
              fieldErrors.currentPassword
                ? 'border-[var(--ee-danger-border)] focus-visible:ring-[var(--ee-danger-text)]'
                : 'border-[var(--ee-border)] focus-visible:ring-[var(--ee-focus-ring)]'
            }`}
          />
          {fieldErrors.currentPassword && (
            <p className="mt-1 text-xs text-[var(--ee-danger-text)]">{fieldErrors.currentPassword}</p>
          )}
        </div>

        <div>
          <label htmlFor="newPassword" className="block text-sm font-medium text-[var(--ee-text)] mb-1">
            Nueva contraseña
          </label>
          <input
            id="newPassword"
            type="password"
            name="newPassword"
            autoComplete="new-password"
            value={newPassword}
            onChange={(e) => {
              setNewPassword(e.target.value);
              if (fieldErrors.newPassword) {
                setFieldErrors((prev) => ({ ...prev, newPassword: undefined }));
              }
            }}
            disabled={isLoading}
            placeholder="••••••••"
            className={`w-full min-h-[44px] px-3 py-2 border rounded-[var(--ee-radius-sm)] bg-[var(--ee-surface)] text-[var(--ee-text)] shadow-sm placeholder:text-[var(--ee-text-muted)] focus-visible:outline-none focus-visible:ring-2 transition-colors ${
              fieldErrors.newPassword
                ? 'border-[var(--ee-danger-border)] focus-visible:ring-[var(--ee-danger-text)]'
                : 'border-[var(--ee-border)] focus-visible:ring-[var(--ee-focus-ring)]'
            }`}
          />
          {fieldErrors.newPassword && (
            <p className="mt-1 text-xs text-[var(--ee-danger-text)]">{fieldErrors.newPassword}</p>
          )}

          {/* Real-time complexity checklist */}
          <div className="mt-2 text-xs space-y-1" aria-live="polite">
            <p className={hasMinLength ? 'text-[var(--ee-success-text)] font-medium' : 'text-[var(--ee-text-muted)]'}>
              {hasMinLength ? '✓' : '•'} Mínimo 8 caracteres
            </p>
            <p className={hasUppercase ? 'text-[var(--ee-success-text)] font-medium' : 'text-[var(--ee-text-muted)]'}>
              {hasUppercase ? '✓' : '•'} Al menos una mayúscula
            </p>
            <p className={hasLowercase ? 'text-[var(--ee-success-text)] font-medium' : 'text-[var(--ee-text-muted)]'}>
              {hasLowercase ? '✓' : '•'} Al menos una minúscula
            </p>
            <p className={hasNumber ? 'text-[var(--ee-success-text)] font-medium' : 'text-[var(--ee-text-muted)]'}>
              {hasNumber ? '✓' : '•'} Al menos un número
            </p>
          </div>
        </div>

        <div>
          <label htmlFor="confirmNewPassword" className="block text-sm font-medium text-[var(--ee-text)] mb-1">
            Confirmar nueva contraseña
          </label>
          <input
            id="confirmNewPassword"
            type="password"
            name="confirmNewPassword"
            autoComplete="new-password"
            value={confirmNewPassword}
            onChange={(e) => {
              setConfirmNewPassword(e.target.value);
              if (fieldErrors.confirmNewPassword) {
                setFieldErrors((prev) => ({ ...prev, confirmNewPassword: undefined }));
              }
            }}
            disabled={isLoading}
            placeholder="••••••••"
            className={`w-full min-h-[44px] px-3 py-2 border rounded-[var(--ee-radius-sm)] bg-[var(--ee-surface)] text-[var(--ee-text)] shadow-sm placeholder:text-[var(--ee-text-muted)] focus-visible:outline-none focus-visible:ring-2 transition-colors ${
              fieldErrors.confirmNewPassword
                ? 'border-[var(--ee-danger-border)] focus-visible:ring-[var(--ee-danger-text)]'
                : 'border-[var(--ee-border)] focus-visible:ring-[var(--ee-focus-ring)]'
            }`}
          />
          {fieldErrors.confirmNewPassword && (
            <p className="mt-1 text-xs text-[var(--ee-danger-text)]">{fieldErrors.confirmNewPassword}</p>
          )}
        </div>

        <Button
          type="submit"
          variant="primary"
          disabled={isLoading}
          loading={isLoading}
          className="w-full min-h-[44px]"
        >
          {isLoading ? 'Actualizando...' : 'Cambiar contraseña y continuar'}
        </Button>
      </form>
    </AuthCardShell>
  );
};

export default FirstLoginPasswordPage;
