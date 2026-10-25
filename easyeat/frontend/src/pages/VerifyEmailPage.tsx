import React, { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { profileApi } from '../api/profileApi';
import { CheckCircle2, AlertTriangle, Loader2, ArrowRight } from 'lucide-react';

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
      } catch (err: any) {
        const msg =
          err.response?.data?.detail ||
          err.message ||
          'El enlace de verificación es inválido, ha expirado o ya fue utilizado.';
        setErrorMessage(msg);
      } finally {
        setLoading(false);
      }
    };

    verifyToken();
  }, [token]);

  return (
    <div className="min-h-screen bg-neutral-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="text-center mb-6">
          <h1 className="text-3xl font-extrabold text-neutral-900 tracking-tight">EasyEat</h1>
          <p className="mt-1 text-sm text-neutral-500">Verificación de correo electrónico</p>
        </div>

        <div className="bg-white py-8 px-6 shadow-sm sm:rounded-2xl border border-neutral-200">
          {loading ? (
            <div className="text-center py-8 space-y-3">
              <Loader2 className="w-10 h-10 text-amber-600 animate-spin mx-auto" />
              <p className="text-sm font-medium text-neutral-600">Validando token de seguridad...</p>
            </div>
          ) : successEmail ? (
            <div className="text-center py-4 space-y-4">
              <div className="w-14 h-14 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto">
                <CheckCircle2 className="w-8 h-8" />
              </div>
              <h2 className="text-xl font-bold text-neutral-900">¡Correo verificado con éxito!</h2>
              <p className="text-sm text-neutral-600">
                Tu dirección de correo principal ahora es <span className="font-semibold text-neutral-900">{successEmail}</span>.
              </p>
              <div className="p-3 bg-neutral-50 border border-neutral-200 rounded-lg text-xs text-neutral-600">
                Por motivos de seguridad, tus sesiones activas han sido cerradas. Por favor, iniciá sesión con tu nuevo correo.
              </div>
              <div className="pt-2">
                <Link
                  to="/login"
                  className="inline-flex items-center justify-center gap-2 w-full py-2.5 px-4 rounded-lg text-sm font-semibold text-white bg-amber-600 hover:bg-amber-700 transition-colors shadow-sm"
                >
                  Iniciar sesión
                  <ArrowRight className="w-4 h-4" />
                </Link>
              </div>
            </div>
          ) : (
            <div className="text-center py-4 space-y-4">
              <div className="w-14 h-14 bg-red-100 text-red-600 rounded-full flex items-center justify-center mx-auto">
                <AlertTriangle className="w-8 h-8" />
              </div>
              <h2 className="text-xl font-bold text-neutral-900">Enlace no válido o expirado</h2>
              <p className="text-sm text-neutral-600">{errorMessage}</p>
              <p className="text-xs text-neutral-500">
                Los enlaces de verificación son de uso único y expiran a las 24 horas. Podés solicitar un nuevo enlace desde tu perfil.
              </p>
              <div className="pt-2 flex flex-col gap-2">
                <Link
                  to="/perfil"
                  className="inline-flex items-center justify-center w-full py-2.5 px-4 rounded-lg text-sm font-semibold text-white bg-amber-600 hover:bg-amber-700 transition-colors shadow-sm"
                >
                  Ir a mi perfil
                </Link>
                <Link
                  to="/login"
                  className="inline-flex items-center justify-center w-full py-2 px-4 rounded-lg text-sm font-medium text-neutral-600 hover:text-neutral-900 transition-colors"
                >
                  Volver al inicio de sesión
                </Link>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default VerifyEmailPage;
