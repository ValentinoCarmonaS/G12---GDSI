import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { useProfile } from '../hooks/useProfile';
import { useCart } from '../hooks/useCart';
import { PersonalDataForm } from '../components/profile/PersonalDataForm';
import { PendingEmailBanner } from '../components/profile/PendingEmailBanner';
import { DietaryRestrictionsSelector } from '../components/profile/DietaryRestrictionsSelector';
import { MyOrdersSection } from '../components/profile/MyOrdersSection';
import { Loader2, CheckCircle, AlertCircle } from 'lucide-react';

type ProfileTab = 'perfil' | 'pedidos';

export const ProfilePage: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  const {
    profile,
    taxonomy,
    loading,
    taxonomyLoading,
    error,
    cooldownSeconds,
    updatePersonalData,
    updateDemographics,
    uploadAvatar,
    removeAvatar,
    refreshProfile,
    updateDietaryRestrictions,
    requestEmailChange,
    resendVerification,
    cancelEmailChange,
  } = useProfile();

  const { items: cartItems, reEvaluateCompliance } = useCart();

  const [activeTab, setActiveTab] = useState<ProfileTab>(() =>
    location.hash === '#pedidos' ? 'pedidos' : 'perfil'
  );
  const [successBanner, setSuccessBanner] = useState<string | null>(null);

  useEffect(() => {
    setActiveTab(location.hash === '#pedidos' ? 'pedidos' : 'perfil');
  }, [location.hash]);

  const handleTabChange = (tab: ProfileTab) => {
    setActiveTab(tab);
    if (tab === 'pedidos' && location.hash !== '#pedidos') {
      navigate('/perfil#pedidos', { replace: true });
    } else if (tab === 'perfil' && location.hash === '#pedidos') {
      navigate('/perfil', { replace: true });
    }
  };

  const handlePersonalDataSubmit = async (data: {
    firstName: string;
    lastName: string;
    phone?: string;
    age?: number;
    gender?: string;
    address?: string;
  }) => {
    setSuccessBanner(null);
    await updatePersonalData({
      firstName: data.firstName,
      lastName: data.lastName,
      phone: data.phone,
    });
    await updateDemographics({
      age: data.age ?? null,
      gender: data.gender ?? null,
      address: data.address ?? null,
    });
    await refreshProfile();
    setSuccessBanner('Tus datos personales y demográficos se han actualizado correctamente.');
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

  const handleDietarySubmit = async (codes: string[]) => {
    setSuccessBanner(null);
    await updateDietaryRestrictions(codes);
    if (cartItems && cartItems.length > 0) {
      await reEvaluateCompliance();
    }
    setSuccessBanner('Tus preferencias dietarias se han actualizado correctamente.');
    setTimeout(() => {
      setSuccessBanner(null);
    }, 4000);
  };

  return (
    <WorkspaceLayout title="Mi perfil">
      <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-neutral-900 tracking-tight">Mi perfil</h1>
          <p className="text-sm text-neutral-600 mt-1">
            Administrá tu información personal, preferencias de cuenta y el seguimiento de tus pedidos.
          </p>
        </div>

        <div
          role="tablist"
          aria-label="Secciones de perfil"
          className="mb-6 flex items-center gap-2 border-b border-neutral-200"
        >
          <button
            type="button"
            role="tab"
            id="tab-perfil"
            aria-selected={activeTab === 'perfil'}
            aria-controls="panel-perfil"
            onClick={() => handleTabChange('perfil')}
            className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              activeTab === 'perfil'
                ? 'border-neutral-900 text-neutral-900'
                : 'border-transparent text-neutral-600 hover:text-neutral-900'
            }`}
          >
            Datos y preferencias
          </button>
          <button
            type="button"
            role="tab"
            id="tab-pedidos"
            aria-selected={activeTab === 'pedidos'}
            aria-controls="panel-pedidos"
            onClick={() => handleTabChange('pedidos')}
            className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              activeTab === 'pedidos'
                ? 'border-neutral-900 text-neutral-900'
                : 'border-transparent text-neutral-600 hover:text-neutral-900'
            }`}
          >
            Mis pedidos
          </button>
        </div>

        {activeTab === 'pedidos' ? (
          <div role="tabpanel" id="panel-pedidos" aria-labelledby="tab-pedidos">
            <MyOrdersSection />
          </div>
        ) : (
          <div role="tabpanel" id="panel-perfil" aria-labelledby="tab-perfil">
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
              <div className="space-y-8">
                {profile?.pendingEmail && (
                  <PendingEmailBanner
                    pendingEmail={profile.pendingEmail}
                    onResend={async () => {
                      await resendVerification();
                    }}
                    onCancel={cancelEmailChange}
                    initialCooldownSeconds={cooldownSeconds}
                  />
                )}

                <PersonalDataForm
                  initialData={profile || undefined}
                  onSubmit={handlePersonalDataSubmit}
                  onRequestEmailChange={handleRequestEmailChange}
                  onUploadAvatar={async (file) => {
                    await uploadAvatar(file);
                  }}
                  onRemoveAvatar={removeAvatar}
                />

                <DietaryRestrictionsSelector
                  availableTaxonomy={taxonomy}
                  selectedCodes={(profile?.dietaryRestrictions || []).map((r) => r.code)}
                  onSave={handleDietarySubmit}
                  loading={taxonomyLoading}
                />
              </div>
            )}
          </div>
        )}
      </div>
    </WorkspaceLayout>
  );
};

export default ProfilePage;
