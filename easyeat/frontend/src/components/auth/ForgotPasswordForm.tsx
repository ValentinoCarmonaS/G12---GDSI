import React, { useState } from 'react';

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
    <form onSubmit={handleSubmit} noValidate className={`space-y-6 ${className}`}>
      {generalError && (
        <div
          role="alert"
          className="p-3 text-sm rounded-md bg-red-50 border border-red-200 text-red-700 animate-fade-in"
        >
          {generalError}
        </div>
      )}

      {isSuccess ? (
        <div
          role="status"
          className="p-4 rounded-md bg-green-50 border border-green-200 text-green-800 text-sm animate-fade-in space-y-2"
        >
          <p className="font-semibold">Solicitud enviada</p>
          <p>
            Si el correo electrónico está registrado, recibirás las instrucciones para restablecer tu contraseña.
          </p>
          {onCancel && (
            <button
              type="button"
              onClick={onCancel}
              className="mt-3 w-full py-2 px-4 border border-green-300 rounded-md text-sm font-medium text-green-700 bg-white hover:bg-green-50 transition-colors"
            >
              Volver al inicio de sesión
            </button>
          )}
        </div>
      ) : (
        <>
          <p className="text-sm text-gray-600">
            Ingresá tu correo electrónico registrado y te enviaremos un enlace de recuperación válido por 15 minutos.
          </p>

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
                if (fieldError) setFieldError(null);
              }}
              disabled={isLoading}
              placeholder="tu@correo.com"
              className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
                fieldError
                  ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
                  : 'border-gray-300 focus:ring-amber-500 focus:border-amber-500'
              } ${isLoading ? 'bg-gray-100 cursor-not-allowed' : 'bg-white'}`}
            />
            {fieldError && (
              <p className="mt-1 text-xs text-red-600" id="email-error">
                {fieldError}
              </p>
            )}
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onCancel}
              disabled={isLoading}
              className="w-1/2 py-2.5 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-amber-500 transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className={`w-1/2 flex justify-center py-2.5 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white transition-all ${
                isLoading
                  ? 'bg-amber-400 cursor-not-allowed opacity-75'
                  : 'bg-amber-600 hover:bg-amber-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-amber-500'
              }`}
            >
              {isLoading ? 'Enviando...' : 'Enviar instrucciones'}
            </button>
          </div>
        </>
      )}
    </form>
  );
};

export default ForgotPasswordForm;
