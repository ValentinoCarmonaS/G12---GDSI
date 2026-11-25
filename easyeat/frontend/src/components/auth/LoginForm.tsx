import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle } from 'lucide-react';
import { LoginCredentials } from '../../types/auth';
import { Button } from '../primitives/Button';
import { FeedbackState } from '../primitives/FeedbackState';

interface LoginFormProps {
  onSubmit?: (credentials: LoginCredentials) => Promise<void>;
  className?: string;
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const LoginForm: React.FC<LoginFormProps> = ({ onSubmit, className = '' }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

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
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Debouncing guard: ignore rapid subsequent clicks
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
      if (error?.response?.status === 401) {
        setGeneralError('Credenciales inválidas');
      } else if (error?.response?.status === 403) {
        setGeneralError(error.response.data?.detail || 'Cuenta suspendida o inactiva');
      } else {
        setGeneralError('Error de conexión con el servidor. Intente nuevamente.');
      }
    } finally {
      setIsLoading(false);
    }
  };

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
          placeholder="ejemplo@comedor.com"
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
          autoComplete="current-password"
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
      </div>

      <div className="flex items-center justify-end text-sm">
        <Link
          to="/forgot-password"
          className="font-semibold text-ee-text underline decoration-ee-primary-border underline-offset-4 hover:decoration-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring rounded-ee-sm"
        >
          Olvidé mi contraseña
        </Link>
      </div>

      <Button
        type="submit"
        variant="primary"
        size="md"
        fullWidth
        loading={isLoading}
        loadingText="Ingresando..."
      >
        Ingresar
      </Button>

      <div className="text-center text-sm pt-2 text-ee-text-secondary">
        <span>¿No tenés una cuenta? </span>
        <Link
          to="/register"
          className="font-semibold text-ee-text underline decoration-ee-primary-border underline-offset-4 hover:decoration-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring rounded-ee-sm"
        >
          Registrate
        </Link>
      </div>
    </form>
  );
};

export default LoginForm;
