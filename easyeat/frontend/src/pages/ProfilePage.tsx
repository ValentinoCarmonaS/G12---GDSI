import React, { useState } from 'react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { useProfile } from '../hooks/useProfile';
import { PersonalDataForm } from '../components/profile/PersonalDataForm';
import { PendingEmailBanner } from '../components/profile/PendingEmailBanner';
import { Loader2, CheckCircle, AlertCircle } from 'lucide-react';

export const ProfilePage: React.FC = () => {
  const {
    profile,
    loading,
    error,
    cooldownSeconds,
    updatePersonalData,
    requestEmailChange,
    resendVerification,
    cancelEmailChange,
  } = useProfile();

  const [successBanner, setSuccessBanner] = useState<string | null>(null);

  const handlePersonalDataSubmit = async (data: { firstName: string; lastName: string; phone?: string }) => {
    setSuccessBanner(null);
    await updatePersonalData(data);
    setSuccessBanner('Tus datos personales se han actualizado correctamente.');
    setTimeout(() => {
      setSuccessBanner(null);
    }, 4000);
  };

  const handleRequestEmailChange = async (newEmail: string) => {
    setSuccessBanner(null);
    await requestEmailChange(newEmail);
    setSuccessBanner(`Se ha enviado un enlace de confirmación a ${newEmail}.`);
    setTimeout(() => {
      setSuccessBanner(null);
    }, 5000);
  };

  return (
    <WorkspaceLayout role="COMENSAL" title="Mi perfil">
      <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-neutral-900 tracking-tight">Mi perfil</h1>
          <p className="text-sm text-neutral-600 mt-1">
            Administrá tu información de contacto personal y preferencias de cuenta.
          </p>
        </div>

        {error && (
          <div
            role="alert"
            className="mb-6 p-4 rounded-xl bg-red-50 border border-red-200 text-red-800 text-sm flex items-center gap-3"
          >
            <AlertCircle className="w-5 h-5 shrink-0 text-red-600" />
            <span>{error}</span>
          </div>
        )}

        {successBanner && (
          <div
            role="status"
            className="mb-6 p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm flex items-center gap-3 animate-fade-in"
          >
            <CheckCircle className="w-5 h-5 shrink-0 text-emerald-600" />
            <span>{successBanner}</span>
          </div>
        )}

        {loading ? (
          <div className="flex flex-col items-center justify-center py-16 space-y-3 bg-white rounded-xl border border-neutral-200">
            <Loader2 className="w-8 h-8 text-amber-600 animate-spin" />
            <p className="text-sm text-neutral-500 font-medium">Cargando perfil...</p>
          </div>
        ) : (
          <div className="space-y-6">
            {profile?.pendingEmail && (
              <PendingEmailBanner
                pendingEmail={profile.pendingEmail}
                onResend={resendVerification}
                onCancel={cancelEmailChange}
                initialCooldownSeconds={cooldownSeconds}
              />
            )}

            <PersonalDataForm
              initialData={profile}
              onSubmit={handlePersonalDataSubmit}
              onRequestEmailChange={handleRequestEmailChange}
            />
          </div>
        )}
      </div>
    </WorkspaceLayout>
  );
};

export default ProfilePage;
