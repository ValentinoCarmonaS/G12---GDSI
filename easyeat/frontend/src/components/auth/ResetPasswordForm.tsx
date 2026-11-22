import React, { useState } from 'react';
import { AlertCircle, Check, Circle } from 'lucide-react';
import { Button } from '../primitives/Button';
import { FeedbackState } from '../primitives/FeedbackState';

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
        <label htmlFor="newPassword" className="block text-sm font-semibold text-ee-text mb-1.5">
          Nueva contraseña
        </label>
        <input
          id="newPassword"
          type="password"
          name="newPassword"
          autoComplete="new-password"
          aria-invalid={Boolean(fieldErrors.newPassword)}
          aria-describedby={fieldErrors.newPassword ? 'newPassword-error' : undefined}
          value={newPassword}
          onChange={(e) => {
            setNewPassword(e.target.value);
            if (fieldErrors.newPassword) setFieldErrors((prev) => ({ ...prev, newPassword: undefined }));
          }}
          disabled={isLoading}
          placeholder="••••••••"
          className={`w-full min-h-[44px] px-3.5 py-2.5 text-sm rounded-ee-sm border text-ee-text placeholder:text-ee-text-secondary/70 transition-colors motion-reduce:transition-none focus:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring ${
            fieldErrors.newPassword ? 'border-ee-danger-border' : 'border-ee-border-strong'
          } ${isLoading ? 'bg-ee-surface-subtle cursor-not-allowed opacity-70' : 'bg-ee-surface-elevated'}`}
        />
        {fieldErrors.newPassword && (
          <p className="mt-1.5 text-xs font-medium text-ee-danger-fg" id="newPassword-error">
            {fieldErrors.newPassword}
          </p>
        )}

        {/* Real-time complexity checklist */}
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

      <div>
        <label htmlFor="confirmPassword" className="block text-sm font-semibold text-ee-text mb-1.5">
          Confirmar contraseña
        </label>
        <input
          id="confirmPassword"
          type="password"
          name="confirmPassword"
          autoComplete="new-password"
          aria-invalid={Boolean(fieldErrors.confirmPassword)}
          aria-describedby={fieldErrors.confirmPassword ? 'confirmPassword-error' : undefined}
          value={confirmPassword}
          onChange={(e) => {
            setConfirmPassword(e.target.value);
            if (fieldErrors.confirmPassword) setFieldErrors((prev) => ({ ...prev, confirmPassword: undefined }));
          }}
          disabled={isLoading}
          placeholder="••••••••"
          className={`w-full min-h-[44px] px-3.5 py-2.5 text-sm rounded-ee-sm border text-ee-text placeholder:text-ee-text-secondary/70 transition-colors motion-reduce:transition-none focus:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring ${
            fieldErrors.confirmPassword ? 'border-ee-danger-border' : 'border-ee-border-strong'
          } ${isLoading ? 'bg-ee-surface-subtle cursor-not-allowed opacity-70' : 'bg-ee-surface-elevated'}`}
        />
        {fieldErrors.confirmPassword && (
          <p className="mt-1.5 text-xs font-medium text-ee-danger-fg" id="confirmPassword-error">
            {fieldErrors.confirmPassword}
          </p>
        )}
      </div>

      <Button
        type="submit"
        variant="primary"
        size="md"
        fullWidth
        loading={isLoading}
        loadingText="Restableciendo..."
      >
        Restablecer contraseña
      </Button>
    </form>
  );
};

export default ResetPasswordForm;
