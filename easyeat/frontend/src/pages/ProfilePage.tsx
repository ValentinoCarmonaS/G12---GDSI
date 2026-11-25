import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { useProfile } from '../hooks/useProfile';
import { useCart } from '../hooks/useCart';
import { PersonalDataForm } from '../components/profile/PersonalDataForm';
import { PendingEmailBanner } from '../components/profile/PendingEmailBanner';
import { DietaryRestrictionsSelector } from '../components/profile/DietaryRestrictionsSelector';
import { MyOrdersSection } from '../components/profile/MyOrdersSection';
import { FeedbackState } from '../components/primitives/FeedbackState';
import { LoadingSkeleton } from '../components/primitives/LoadingSkeleton';

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
      <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <div className="mb-6">
          <h1 className="font-ee-display text-2xl font-bold tracking-tight text-ee-text sm:text-3xl">
            Mi perfil
          </h1>
          <p className="mt-1 text-sm text-ee-text-secondary">
            Administrá tu información personal, preferencias de cuenta y el seguimiento de tus pedidos.
          </p>
        </div>

        <div
          role="tablist"
          aria-label="Secciones de perfil"
          data-bento-tablist="true"
          className="mb-6 inline-flex flex-wrap items-center gap-1.5 rounded-ee-md border border-ee-border-subtle bg-ee-surface-subtle p-1.5"
        >
          <button
            type="button"
            role="tab"
            id="tab-perfil"
            aria-selected={activeTab === 'perfil'}
            aria-controls="panel-perfil"
            onClick={() => handleTabChange('perfil')}
            className={`min-h-[44px] rounded-ee-sm px-4 py-2 text-sm font-semibold transition-colors ${
              activeTab === 'perfil'
                ? 'bg-ee-surface-elevated text-ee-text shadow-ee-elevated'
                : 'text-ee-text-secondary hover:text-ee-text'
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
            className={`min-h-[44px] rounded-ee-sm px-4 py-2 text-sm font-semibold transition-colors ${
              activeTab === 'pedidos'
                ? 'bg-ee-surface-elevated text-ee-text shadow-ee-elevated'
                : 'text-ee-text-secondary hover:text-ee-text'
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
          <div role="tabpanel" id="panel-perfil" aria-labelledby="tab-perfil" className="space-y-6">
            {error && <FeedbackState variant="danger" role="alert" title="Error en el perfil" message={error} />}

            {successBanner && (
              <FeedbackState variant="success" title="Cambios guardados" message={successBanner} />
            )}

            {loading ? (
              <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
                <LoadingSkeleton variant="block" />
                <LoadingSkeleton variant="block" />
              </div>
            ) : (
              <div className="space-y-6">
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

                <div data-testid="profile-bento-grid" className="grid grid-cols-1 gap-6 lg:grid-cols-2">
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
              </div>
            )}
          </div>
        )}
      </div>
    </WorkspaceLayout>
  );
};

export default ProfilePage;
