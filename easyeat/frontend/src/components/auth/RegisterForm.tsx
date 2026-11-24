import React, { useState } from 'react';
import { AlertCircle, Check, Circle } from 'lucide-react';
import { RegisterCredentials } from '../../types/auth';
import { Button } from '../primitives/Button';
import { FeedbackState } from '../primitives/FeedbackState';

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

  const criteria = [
    { valid: hasMinLength, label: 'Mínimo 8 caracteres' },
    { valid: hasUppercase, label: 'Al menos una mayúscula' },
    { valid: hasLowercase, label: 'Al menos una minúscula' },
    { valid: hasNumber, label: 'Al menos un número' },
  ];

  return (
    <form onSubmit={handleSubmit} noValidate className={`space-y-5 ${className}`}>
      {generalError && (
        <FeedbackState
          variant="danger"
          icon={<AlertCircle className="h-4 w-4" />}
          message={generalError}
        />
      )}

      <div>
        <label htmlFor="email" className="block text-sm font-semibold text-ee-text mb-1.5">
          Correo electrónico
        </label>
        <input
          id="email"
          type="email"
          name="email"
          autoComplete="email"
          aria-invalid={Boolean(fieldErrors.email)}
          aria-describedby={fieldErrors.email ? 'email-error' : undefined}
          value={email}
          onChange={(e) => {
            setEmail(e.target.value);
            if (fieldErrors.email) setFieldErrors((prev) => ({ ...prev, email: undefined }));
          }}
          disabled={isLoading}
          placeholder="comensal@ejemplo.com"
          className={`w-full min-h-[44px] px-3.5 py-2.5 text-sm rounded-ee-sm border text-ee-text placeholder:text-ee-text-secondary/70 transition-colors motion-reduce:transition-none focus:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring ${
            fieldErrors.email ? 'border-ee-danger-border' : 'border-ee-border-strong'
          } ${isLoading ? 'bg-ee-surface-subtle cursor-not-allowed opacity-70' : 'bg-ee-surface-elevated'}`}
        />
        {fieldErrors.email && (
          <p className="mt-1.5 text-xs font-medium text-ee-danger-fg" id="email-error">
            {fieldErrors.email}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="password" className="block text-sm font-semibold text-ee-text mb-1.5">
          Contraseña
        </label>
        <input
          id="password"
          type="password"
          name="password"
          autoComplete="new-password"
          aria-invalid={Boolean(fieldErrors.password)}
          aria-describedby={fieldErrors.password ? 'password-error' : undefined}
          value={password}
          onChange={(e) => {
            setPassword(e.target.value);
            if (fieldErrors.password) setFieldErrors((prev) => ({ ...prev, password: undefined }));
          }}
          disabled={isLoading}
          placeholder="••••••••"
          className={`w-full min-h-[44px] px-3.5 py-2.5 text-sm rounded-ee-sm border text-ee-text placeholder:text-ee-text-secondary/70 transition-colors motion-reduce:transition-none focus:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring ${
            fieldErrors.password ? 'border-ee-danger-border' : 'border-ee-border-strong'
          } ${isLoading ? 'bg-ee-surface-subtle cursor-not-allowed opacity-70' : 'bg-ee-surface-elevated'}`}
        />
        {fieldErrors.password && (
          <p className="mt-1.5 text-xs font-medium text-ee-danger-fg" id="password-error">
            {fieldErrors.password}
          </p>
        )}

        {/* Real-time password complexity checklist (FR-003, FR-018) */}
        <div className="mt-3 p-3.5 bg-ee-surface-subtle border border-ee-border-subtle rounded-ee-sm text-xs space-y-1.5">
          <p className="font-semibold text-ee-text mb-1">Requisitos de la contraseña:</p>
          {criteria.map((item) => (
            <div
              key={item.label}
              data-valid={item.valid ? 'true' : 'false'}
              className={`flex items-center gap-2 ${
                item.valid ? 'text-ee-success-fg font-semibold' : 'text-ee-text-secondary'
              }`}
            >
              {item.valid ? (
                <Check aria-hidden="true" className="h-3.5 w-3.5 shrink-0" />
              ) : (
                <Circle aria-hidden="true" className="h-3 w-3 shrink-0" />
              )}
              <span data-valid={item.valid ? 'true' : 'false'}>{item.label}</span>
            </div>
          ))}
        </div>
      </div>

      <Button
        type="submit"
        variant="primary"
        size="md"
        fullWidth
        loading={isLoading}
        loadingText="Registrando..."
      >
        Registrarse
      </Button>
    </form>
  );
};

export default RegisterForm;
