import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ForgotPasswordForm } from '../components/auth/ForgotPasswordForm';
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
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="text-center">
          <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight">EasyEat</h2>
          <p className="mt-2 text-sm text-gray-600">Recuperación de contraseña</p>
        </div>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10 border border-gray-100">
          <ForgotPasswordForm onSubmit={handleRequestReset} onCancel={handleCancel} />
        </div>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
