import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { useAuth } from '../hooks/useAuth';
import { getRoleDefaultRoute } from '../router/postAuthRouting';

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
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="text-center">
          <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight">EasyEat</h2>
          <h3 className="mt-2 text-xl font-bold text-gray-800">Cambio Obligatorio de Contraseña</h3>
          <p className="mt-1 text-sm text-gray-600">
            Por motivos de seguridad, debés actualizar tu contraseña temporal antes de comenzar a operar.
          </p>
        </div>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10 border border-gray-100">
          <form onSubmit={handleSubmit} noValidate className="space-y-6">
            {generalError && (
              <div
                role="alert"
                className="p-3 text-sm rounded-md bg-red-50 border border-red-200 text-red-700 animate-fade-in"
              >
                {generalError}
              </div>
            )}

            <div>
              <label htmlFor="currentPassword" className="block text-sm font-medium text-gray-700 mb-1">
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
                className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
                  fieldErrors.currentPassword
                    ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
                    : 'border-gray-300 focus:ring-primary-500 focus:border-primary-500'
                }`}
              />
              {fieldErrors.currentPassword && (
                <p className="mt-1 text-xs text-red-600">{fieldErrors.currentPassword}</p>
              )}
            </div>

            <div>
              <label htmlFor="newPassword" className="block text-sm font-medium text-gray-700 mb-1">
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
                className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
                  fieldErrors.newPassword
                    ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
                    : 'border-gray-300 focus:ring-primary-500 focus:border-primary-500'
                }`}
              />
              {fieldErrors.newPassword && (
                <p className="mt-1 text-xs text-red-600">{fieldErrors.newPassword}</p>
              )}

              {/* Real-time complexity checklist */}
              <div className="mt-2 text-xs space-y-1">
                <p className={hasMinLength ? 'text-green-600' : 'text-gray-500'}>
                  {hasMinLength ? '✓' : '•'} Mínimo 8 caracteres
                </p>
                <p className={hasUppercase ? 'text-green-600' : 'text-gray-500'}>
                  {hasUppercase ? '✓' : '•'} Al menos una mayúscula
                </p>
                <p className={hasLowercase ? 'text-green-600' : 'text-gray-500'}>
                  {hasLowercase ? '✓' : '•'} Al menos una minúscula
                </p>
                <p className={hasNumber ? 'text-green-600' : 'text-gray-500'}>
                  {hasNumber ? '✓' : '•'} Al menos un número
                </p>
              </div>
            </div>

            <div>
              <label htmlFor="confirmNewPassword" className="block text-sm font-medium text-gray-700 mb-1">
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
                className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
                  fieldErrors.confirmNewPassword
                    ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
                    : 'border-gray-300 focus:ring-primary-500 focus:border-primary-500'
                }`}
              />
              {fieldErrors.confirmNewPassword && (
                <p className="mt-1 text-xs text-red-600">{fieldErrors.confirmNewPassword}</p>
              )}
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 transition-colors"
            >
              {isLoading ? 'Actualizando...' : 'Cambiar contraseña y continuar'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default FirstLoginPasswordPage;
