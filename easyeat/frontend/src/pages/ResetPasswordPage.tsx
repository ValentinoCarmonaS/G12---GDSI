import React, { useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { ResetPasswordForm } from '../components/auth/ResetPasswordForm';
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
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="text-center">
          <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight">EasyEat</h2>
          <p className="mt-2 text-sm text-gray-600">Restablecé tu contraseña</p>
        </div>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10 border border-gray-100">
          {!token ? (
            <div
              role="alert"
              className="p-4 rounded-md bg-red-50 border border-red-200 text-red-800 text-center space-y-3"
            >
              <p className="font-semibold text-base">Enlace de recuperación inválido</p>
              <p className="text-sm">
                No se encontró un token válido en el enlace. Solicitá un nuevo correo de recuperación.
              </p>
              <div className="pt-2">
                <Link
                  to="/forgot-password"
                  className="inline-block py-2 px-4 rounded-md text-sm font-medium text-white bg-amber-600 hover:bg-amber-700 transition-colors"
                >
                  Solicitar nuevo enlace
                </Link>
              </div>
            </div>
          ) : successMessage ? (
            <div
              role="status"
              className="p-4 rounded-md bg-green-50 border border-green-200 text-green-800 text-center animate-fade-in space-y-2"
            >
              <div className="text-lg font-semibold mb-1">Contraseña restablecida</div>
              <p className="text-sm">{successMessage}</p>
            </div>
          ) : (
            <ResetPasswordForm token={token} onSubmit={handleReset} />
          )}
        </div>
      </div>
    </div>
  );
};

export default ResetPasswordPage;
