import React, { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { CheckCircle2, AlertTriangle, ArrowRight } from 'lucide-react';
import { profileApi } from '../api/profileApi';
import { AuthCardShell } from '../components/diner';
import { StatusBadge } from '../components/primitives/StatusBadge';
import { FeedbackState } from '../components/primitives/FeedbackState';
import { LoadingSkeleton } from '../components/primitives/LoadingSkeleton';

export const VerifyEmailPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [loading, setLoading] = useState<boolean>(true);
  const [successEmail, setSuccessEmail] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setLoading(false);
      setErrorMessage('No se encontró ningún token de verificación en el enlace proporcionado.');
      return;
    }

    const verifyToken = async () => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const response = await profileApi.confirmEmailChange({ token });
        setSuccessEmail(response.activeEmail);
      } catch (err: unknown) {
        const error = err as { response?: { data?: { detail?: string } }; message?: string };
        const msg =
          error.response?.data?.detail ||
          error.message ||
          'El enlace de verificación es inválido, ha expirado o ya fue utilizado.';
        setErrorMessage(msg);
      } finally {
        setLoading(false);
      }
    };

    verifyToken();
  }, [token]);

  return (
    <AuthCardShell
      title="Verificación de correo"
      subtitle="Confirmación de dirección de correo electrónico"
    >
      {loading ? (
        <div className="space-y-4 py-4">
          <LoadingSkeleton variant="text" lines={3} label="Validando token de seguridad..." />
          <p className="text-center text-sm font-medium text-ee-text-secondary">
            Validando token de seguridad...
          </p>
        </div>
      ) : successEmail ? (
        <div className="space-y-5">
          <div className="flex flex-col items-start gap-3">
            <StatusBadge variant="success" icon={<CheckCircle2 className="h-3.5 w-3.5" />}>
              Verificado
            </StatusBadge>
            <h2 className="font-ee-display text-xl font-bold text-ee-text">
              ¡Correo verificado con éxito!
            </h2>
            <p className="text-sm text-ee-text-secondary leading-relaxed">
              Tu dirección de correo principal ahora es{' '}
              <span className="font-semibold text-ee-text">{successEmail}</span>.
            </p>
          </div>

          <FeedbackState
            variant="info"
            message="Por motivos de seguridad, tus sesiones activas han sido cerradas. Por favor, iniciá sesión con tu nuevo correo."
          />

          <Link
            to="/login"
            className="inline-flex min-h-[44px] w-full items-center justify-center gap-2 rounded-ee-sm border border-ee-primary-border bg-ee-primary px-4 py-2 text-sm font-semibold text-ee-on-primary shadow-ee-elevated transition-colors motion-reduce:transition-none hover:bg-ee-primary-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring"
          >
            <span>Iniciar sesión</span>
            <ArrowRight aria-hidden="true" className="h-4 w-4" />
          </Link>
        </div>
      ) : (
        <div className="space-y-5">
          <div className="flex flex-col items-start gap-2">
            <StatusBadge variant="danger" icon={<AlertTriangle className="h-3.5 w-3.5" />}>
              Enlace inválido
            </StatusBadge>
            <h2 className="font-ee-display text-xl font-bold text-ee-text">
              Enlace no válido o expirado
            </h2>
          </div>

          <FeedbackState
            variant="danger"
            icon={<AlertTriangle className="h-4 w-4" />}
            message={errorMessage || 'El enlace de verificación es inválido o expiró.'}
          />

          <p className="text-xs text-ee-text-secondary leading-relaxed">
            Los enlaces de verificación son de uso único y expiran a las 24 horas. Podés solicitar un nuevo enlace desde tu perfil.
          </p>

          <div className="flex flex-col gap-2.5 pt-1">
            <Link
              to="/perfil"
              className="inline-flex min-h-[44px] w-full items-center justify-center rounded-ee-sm border border-ee-primary-border bg-ee-primary px-4 py-2 text-sm font-semibold text-ee-on-primary shadow-ee-elevated transition-colors motion-reduce:transition-none hover:bg-ee-primary-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring"
            >
              Ir a mi perfil
            </Link>
            <Link
              to="/login"
              className="inline-flex min-h-[44px] w-full items-center justify-center rounded-ee-sm border border-ee-border-strong bg-ee-surface-elevated px-4 py-2 text-sm font-semibold text-ee-text transition-colors motion-reduce:transition-none hover:bg-ee-surface-subtle focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring"
            >
              Volver al inicio de sesión
            </Link>
          </div>
        </div>
      )}
    </AuthCardShell>
  );
};

export default VerifyEmailPage;
