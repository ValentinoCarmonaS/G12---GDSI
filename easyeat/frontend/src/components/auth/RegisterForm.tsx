import React, { useState } from 'react';
import { RegisterCredentials } from '../../types/auth';

interface RegisterFormProps {
  onSubmit?: (credentials: RegisterCredentials) => Promise<void>;
  className?: string;
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const RegisterForm: React.FC<RegisterFormProps> = ({ onSubmit, className = '' }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // Real-time password criteria
  const hasMinLength = password.length >= 8;
  const hasUppercase = /[A-Z]/.test(password);
  const hasLowercase = /[a-z]/.test(password);
  const hasNumber = /[0-9]/.test(password);
  const isPasswordValid = hasMinLength && hasUppercase && hasLowercase && hasNumber;

  const validate = (): boolean => {
    const errors: { email?: string; password?: string } = {};
    const trimmedEmail = email.trim();

    if (!trimmedEmail) {
      errors.email = 'El correo electrónico es obligatorio';
    } else if (!EMAIL_REGEX.test(trimmedEmail)) {
      errors.email = 'Formato de correo electrónico inválido';
    }

    if (!password) {
      errors.password = 'La contraseña es obligatoria';
    } else if (!isPasswordValid) {
      errors.password = 'La contraseña no cumple con los requisitos mínimos de seguridad';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Debouncing guard
    if (isLoading) {
      return;
    }

    setGeneralError(null);

    if (!validate()) {
      return;
    }

    const normalizedEmail = email.trim().toLowerCase();

    setIsLoading(true);
    try {
      if (onSubmit) {
        await onSubmit({ email: normalizedEmail, password });
      }
    } catch (err: unknown) {
      const error = err as { response?: { status?: number; data?: { detail?: string } }; message?: string };
      if (error?.response?.status === 409) {
        setGeneralError(error.response.data?.detail || 'El correo electrónico ya se encuentra registrado');
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
        <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">
          Correo electrónico
        </label>
        <input
          id="email"
          type="email"
          name="email"
          autoComplete="email"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value);
            if (fieldErrors.email) setFieldErrors((prev) => ({ ...prev, email: undefined }));
          }}
          disabled={isLoading}
          placeholder="comensal@ejemplo.com"
          className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
            fieldErrors.email
              ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
              : 'border-gray-300 focus:ring-amber-500 focus:border-amber-500'
          } ${isLoading ? 'bg-gray-100 cursor-not-allowed' : 'bg-white'}`}
        />
        {fieldErrors.email && (
          <p className="mt-1 text-xs text-red-600" id="email-error">
            {fieldErrors.email}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
          Contraseña
        </label>
        <input
          id="password"
          type="password"
          name="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value);
            if (fieldErrors.password) setFieldErrors((prev) => ({ ...prev, password: undefined }));
          }}
          disabled={isLoading}
          placeholder="••••••••"
          className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
            fieldErrors.password
              ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
              : 'border-gray-300 focus:ring-amber-500 focus:border-amber-500'
          } ${isLoading ? 'bg-gray-100 cursor-not-allowed' : 'bg-white'}`}
        />
        {fieldErrors.password && (
          <p className="mt-1 text-xs text-red-600" id="password-error">
            {fieldErrors.password}
          </p>
        )}

        {/* Real-time password complexity checklist (FR-003, FR-018) */}
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

      <button
        type="submit"
        disabled={isLoading}
        className={`w-full flex justify-center py-2.5 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white transition-all ${
          isLoading
            ? 'bg-amber-400 cursor-not-allowed opacity-75'
            : 'bg-amber-600 hover:bg-amber-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-amber-500'
        }`}
      >
        {isLoading ? (
          <span className="flex items-center gap-2">
            <svg
              className="animate-spin h-4 w-4 text-white"
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
              ></circle>
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              ></path>
            </svg>
            Registrando...
          </span>
        ) : (
          'Registrarse'
        )}
      </button>
    </form>
  );
};

export default RegisterForm;
