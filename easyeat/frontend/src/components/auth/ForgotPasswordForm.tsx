import React, { useState } from 'react';
import { AlertCircle, CheckCircle2 } from 'lucide-react';
import { Button } from '../primitives/Button';
import { FeedbackState } from '../primitives/FeedbackState';

interface ForgotPasswordFormProps {
  onSubmit?: (data: { email: string }) => Promise<void>;
  onCancel?: () => void;
  className?: string;
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const ForgotPasswordForm: React.FC<ForgotPasswordFormProps> = ({
  onSubmit,
  onCancel,
  className = '',
}) => {
  const [email, setEmail] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [isSuccess, setIsSuccess] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (isLoading) return;

    setFieldError(null);
    setGeneralError(null);

    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      setFieldError('El correo electrónico es obligatorio');
      return;
    }

    if (!EMAIL_REGEX.test(trimmedEmail)) {
      setFieldError('Formato de correo electrónico inválido');
      return;
    }

    const normalizedEmail = trimmedEmail.toLowerCase();

    setIsLoading(true);
    try {
      if (onSubmit) {
        await onSubmit({ email: normalizedEmail });
      }
      setIsSuccess(true);
    } catch {
      // Anti-enumeration guarantee: uniform behavior even on failure or show connection error
      setGeneralError('Error de conexión con el servidor. Intente nuevamente.');
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

      {isSuccess ? (
        <div className="space-y-4">
          <FeedbackState
            variant="success"
            icon={<CheckCircle2 className="h-5 w-5" />}
            title="Solicitud enviada"
            message="Si el correo electrónico está registrado, recibirás las instrucciones para restablecer tu contraseña."
          />
          {onCancel && (
            <Button type="button" variant="outline" size="md" fullWidth onClick={onCancel}>
              Volver al inicio de sesión
            </Button>
          )}
        </div>
      ) : (
        <>
          <p className="text-sm text-ee-text-secondary leading-relaxed">
            Ingresá tu correo electrónico registrado y te enviaremos un enlace de recuperación válido por 15 minutos.
          </p>

          <div>
            <label htmlFor="email" className="block text-sm font-semibold text-ee-text mb-1.5">
              Correo electrónico
            </label>
            <input
              id="email"
              type="email"
              name="email"
              autoComplete="email"
              aria-invalid={Boolean(fieldError)}
              aria-describedby={fieldError ? 'email-error' : undefined}
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (fieldError) setFieldError(null);
              }}
              disabled={isLoading}
              placeholder="tu@correo.com"
              className={`w-full min-h-[44px] px-3.5 py-2.5 text-sm rounded-ee-sm border text-ee-text placeholder:text-ee-text-secondary/70 transition-colors motion-reduce:transition-none focus:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring ${
                fieldError ? 'border-ee-danger-border' : 'border-ee-border-strong'
              } ${isLoading ? 'bg-ee-surface-subtle cursor-not-allowed opacity-70' : 'bg-ee-surface-elevated'}`}
            />
            {fieldError && (
              <p className="mt-1.5 text-xs font-medium text-ee-danger-fg" id="email-error">
                {fieldError}
              </p>
            )}
          </div>

          <div className="flex flex-col-reverse sm:flex-row items-center gap-3">
            <Button
              type="button"
              variant="outline"
              size="md"
              fullWidth
              onClick={onCancel}
              disabled={isLoading}
            >
              Cancelar
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="md"
              fullWidth
              loading={isLoading}
              loadingText="Enviando..."
            >
              Enviar instrucciones
            </Button>
          </div>
        </>
      )}
    </form>
  );
};

export default ForgotPasswordForm;
