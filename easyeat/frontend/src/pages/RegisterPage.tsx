import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { RegisterForm } from '../components/auth/RegisterForm';
import { authApi } from '../api/authApi';
import { RegisterCredentials } from '../types/auth';

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const handleRegister = async (credentials: RegisterCredentials) => {
    await authApi.register(credentials);
    setSuccessMessage('¡Cuenta creada con éxito! Redirigiendo al inicio de sesión...');
    setTimeout(() => {
      navigate('/login', {
        replace: true,
        state: { reason: 'Registro exitoso. Ya podés iniciar sesión con tus credenciales.' },
      });
    }, 1500);
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="text-center">
          <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight">EasyEat</h2>
          <p className="mt-2 text-sm text-gray-600">Creá tu cuenta para empezar a pedir</p>
        </div>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10 border border-gray-100">
          {successMessage ? (
            <div
              role="status"
              className="p-4 rounded-md bg-green-50 border border-green-200 text-green-800 text-center animate-fade-in"
            >
              <div className="text-lg font-semibold mb-1">Registro exitoso</div>
              <p className="text-sm">{successMessage}</p>
            </div>
          ) : (
            <>
              <RegisterForm onSubmit={handleRegister} />

              <div className="mt-6 text-center text-sm">
                <span className="text-gray-500">¿Ya tenés una cuenta? </span>
                <Link
                  to="/login"
                  className="font-medium text-amber-600 hover:text-amber-500 transition-colors"
                >
                  Iniciar sesión
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;
