import React, { useState } from 'react';

interface ResetPasswordFormProps {
  token: string;
  onSubmit?: (data: { token: string; new_password: string }) => Promise<void>;
  className?: string;
}

export const ResetPasswordForm: React.FC<ResetPasswordFormProps> = ({
  token,
  onSubmit,
  className = '',
}) => {
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ newPassword?: string; confirmPassword?: string }>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // Real-time criteria checks
  const hasMinLength = newPassword.length >= 8;
  const hasUppercase = /[A-Z]/.test(newPassword);
  const hasLowercase = /[a-z]/.test(newPassword);
  const hasNumber = /[0-9]/.test(newPassword);
  const isComplexityValid = hasMinLength && hasUppercase && hasLowercase && hasNumber;

  const validate = (): boolean => {
    const errors: { newPassword?: string; confirmPassword?: string } = {};

    if (!newPassword) {
      errors.newPassword = 'La nueva contraseña es obligatoria';
    } else if (!isComplexityValid) {
      errors.newPassword = 'La contraseña no cumple con los requisitos mínimos de seguridad';
    }

    if (!confirmPassword) {
      errors.confirmPassword = 'Debés confirmar la contraseña';
    } else if (newPassword && confirmPassword && newPassword !== confirmPassword) {
      errors.confirmPassword = 'Las contraseñas no coinciden';
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
      if (onSubmit) {
        await onSubmit({ token, new_password: newPassword });
      }
    } catch (err: unknown) {
      const error = err as { response?: { status?: number; data?: { detail?: string } }; message?: string };
      if (error?.response?.status === 400) {
        setGeneralError(error.response.data?.detail || 'El enlace ha expirado o ya ha sido utilizado');
      } else {
        setGeneralError('Error de conexión con el servidor. Intente nuevamente.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} noValidate className={`space-y-6 ${className}`}>
      {generalError && (
        <div
          role="alert"
          className="p-3 text-sm rounded-md bg-red-50 border border-red-200 text-red-700 animate-fade-in"
        >
          {generalError}
        </div>
      )}

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
            if (fieldErrors.newPassword) setFieldErrors((prev) => ({ ...prev, newPassword: undefined }));
          }}
          disabled={isLoading}
          placeholder="••••••••"
          className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
            fieldErrors.newPassword
              ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
              : 'border-gray-300 focus:ring-amber-500 focus:border-amber-500'
          } ${isLoading ? 'bg-gray-100 cursor-not-allowed' : 'bg-white'}`}
        />
        {fieldErrors.newPassword && (
          <p className="mt-1 text-xs text-red-600" id="newPassword-error">
            {fieldErrors.newPassword}
          </p>
        )}

        {/* Real-time complexity checklist */}
        <div className="mt-3 p-3 bg-gray-50 border border-gray-200 rounded-md text-xs space-y-1.5">
          <p className="font-medium text-gray-600 mb-1">Requisitos de la contraseña:</p>
          <div
            data-valid={hasMinLength ? 'true' : 'false'}
            className={`flex items-center gap-1.5 ${hasMinLength ? 'text-green-700 font-medium' : 'text-gray-500'}`}
          >
            <span>{hasMinLength ? '✓' : '•'}</span>
            <span data-valid={hasMinLength ? 'true' : 'false'}>Mínimo 8 caracteres</span>
          </div>
          <div
            data-valid={hasUppercase ? 'true' : 'false'}
            className={`flex items-center gap-1.5 ${hasUppercase ? 'text-green-700 font-medium' : 'text-gray-500'}`}
          >
            <span>{hasUppercase ? '✓' : '•'}</span>
            <span data-valid={hasUppercase ? 'true' : 'false'}>Al menos una mayúscula</span>
          </div>
          <div
            data-valid={hasLowercase ? 'true' : 'false'}
            className={`flex items-center gap-1.5 ${hasLowercase ? 'text-green-700 font-medium' : 'text-gray-500'}`}
          >
            <span>{hasLowercase ? '✓' : '•'}</span>
            <span data-valid={hasLowercase ? 'true' : 'false'}>Al menos una minúscula</span>
          </div>
          <div
            data-valid={hasNumber ? 'true' : 'false'}
            className={`flex items-center gap-1.5 ${hasNumber ? 'text-green-700 font-medium' : 'text-gray-500'}`}
          >
            <span>{hasNumber ? '✓' : '•'}</span>
            <span data-valid={hasNumber ? 'true' : 'false'}>Al menos un número</span>
          </div>
        </div>
      </div>

      <div>
        <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700 mb-1">
          Confirmar contraseña
        </label>
        <input
          id="confirmPassword"
          type="password"
          name="confirmPassword"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={(e) => {
            setConfirmPassword(e.target.value);
            if (fieldErrors.confirmPassword) setFieldErrors((prev) => ({ ...prev, confirmPassword: undefined }));
          }}
          disabled={isLoading}
          placeholder="••••••••"
          className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
            fieldErrors.confirmPassword
              ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
              : 'border-gray-300 focus:ring-amber-500 focus:border-amber-500'
          } ${isLoading ? 'bg-gray-100 cursor-not-allowed' : 'bg-white'}`}
        />
        {fieldErrors.confirmPassword && (
          <p className="mt-1 text-xs text-red-600" id="confirmPassword-error">
            {fieldErrors.confirmPassword}
          </p>
        )}
      </div>

      <button
        type="submit"
        disabled={isLoading}
        className={`w-full flex justify-center py-2.5 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white transition-all ${
          isLoading
            ? 'bg-amber-400 cursor-not-allowed opacity-75'
            : 'bg-amber-600 hover:bg-amber-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-amber-500'
        }`}
      >
        {isLoading ? 'Restableciendo...' : 'Restablecer contraseña'}
      </button>
    </form>
  );
};

export default ResetPasswordForm;
