import React, { useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { AlertTriangle, CheckCircle2 } from 'lucide-react';
import { ResetPasswordForm } from '../components/auth/ResetPasswordForm';
import { AuthCardShell } from '../components/diner';
import { FeedbackState } from '../components/primitives/FeedbackState';
import { authApi } from '../api/authApi';

export const ResetPasswordPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const handleReset = async (data: { token: string; new_password: string }) => {
    await authApi.confirmPasswordReset(data);
    setSuccessMessage('¡Contraseña restablecida con éxito! Redirigiendo al inicio de sesión...');
    setTimeout(() => {
      navigate('/login', {
        replace: true,
        state: { reason: 'Contraseña actualizada con éxito. Iniciá sesión con tu nueva contraseña.' },
      });
    }, 1500);
  };

  return (
    <AuthCardShell
      title="Restablecer contraseña"
      subtitle="Definí una nueva contraseña segura para tu cuenta"
    >
      {!token ? (
        <div className="space-y-4">
          <FeedbackState
            variant="danger"
            icon={<AlertTriangle className="h-5 w-5" />}
            title="Enlace de recuperación inválido"
            message="No se encontró un token válido en el enlace. Solicitá un nuevo correo de recuperación."
          />
          <Link
            to="/forgot-password"
            className="inline-flex min-h-[44px] w-full items-center justify-center rounded-ee-sm border border-ee-primary-border bg-ee-primary px-4 py-2 text-sm font-semibold text-ee-on-primary shadow-ee-elevated transition-colors motion-reduce:transition-none hover:bg-ee-primary-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring"
          >
            Solicitar nuevo enlace
          </Link>
        </div>
      ) : successMessage ? (
        <FeedbackState
          variant="success"
          icon={<CheckCircle2 className="h-5 w-5" />}
          title="Contraseña restablecida"
          message={successMessage}
        />
      ) : (
        <ResetPasswordForm token={token} onSubmit={handleReset} />
      )}
    </AuthCardShell>
  );
};

export default ResetPasswordPage;
