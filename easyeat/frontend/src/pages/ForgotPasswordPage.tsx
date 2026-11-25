import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ForgotPasswordForm } from '../components/auth/ForgotPasswordForm';
import { AuthCardShell } from '../components/diner';
import { authApi } from '../api/authApi';

export const ForgotPasswordPage: React.FC = () => {
  const navigate = useNavigate();

  const handleRequestReset = async (data: { email: string }) => {
    await authApi.requestPasswordReset(data);
  };

  const handleCancel = () => {
    navigate('/login');
  };

  return (
    <AuthCardShell
      title="Recuperación de contraseña"
      subtitle="Te enviaremos un enlace temporal a tu correo registrado"
    >
      <ForgotPasswordForm onSubmit={handleRequestReset} onCancel={handleCancel} />
    </AuthCardShell>
  );
};

export default ForgotPasswordPage;
