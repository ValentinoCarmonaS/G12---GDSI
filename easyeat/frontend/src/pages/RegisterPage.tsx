import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import { RegisterForm } from '../components/auth/RegisterForm';
import { AuthCardShell } from '../components/diner';
import { FeedbackState } from '../components/primitives/FeedbackState';
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
    <AuthCardShell
      title="Crear cuenta"
      subtitle="Creá tu cuenta para empezar a pedir"
      maxWidth="md"
      footer={
        !successMessage ? (
          <div className="text-center">
            <span>¿Ya tenés una cuenta? </span>
            <Link
              to="/login"
              className="font-semibold text-ee-text underline decoration-ee-primary-border underline-offset-4 hover:decoration-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring rounded-ee-sm"
            >
              Iniciar sesión
            </Link>
          </div>
        ) : undefined
      }
    >
      {successMessage ? (
        <FeedbackState
          variant="success"
          icon={<CheckCircle2 className="h-5 w-5" />}
          title="Registro exitoso"
          message={successMessage}
        />
      ) : (
        <RegisterForm onSubmit={handleRegister} />
      )}
    </AuthCardShell>
  );
};

export default RegisterPage;
