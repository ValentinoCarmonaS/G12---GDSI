import React, { useState, useEffect } from 'react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { GlassCard } from '../components/admin/GlassCard';
import { Button } from '../components/primitives/Button';
import { useAuth } from '../hooks/useAuth';
import {
  getAdminBranding,
  updateAdminBranding,
  uploadRestaurantLogo,
  RestaurantBranding,
} from '../api/brandingApi';
import { ColorPickerInput } from '../components/admin/ColorPickerInput';
import { LogoUploader } from '../components/admin/LogoUploader';
import { getOptimalContrastColor, isValidHexColor } from '../utils/colorContrastUtils';
import { resolveApiMediaUrl } from '../utils/mediaUtils';
import { CheckCircle2, AlertCircle, Eye, Store } from 'lucide-react';

export const AdminBrandingPage: React.FC = () => {
  const { user } = useAuth();
  const establishmentId = user?.tenantId;

  const [fantasyName, setFantasyName] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [primaryColor, setPrimaryColor] = useState<string>('#10B981');
  const [secondaryColor, setSecondaryColor] = useState<string>('#065F46');
  const [accentColor, setAccentColor] = useState<string>('#F59E0B');
  const [logoUrl, setLogoUrl] = useState<string | null>(null);
  const [previewImageError, setPreviewImageError] = useState<boolean>(false);

  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isUploadingLogo, setIsUploadingLogo] = useState<boolean>(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    setPreviewImageError(false);
  }, [logoUrl]);

  useEffect(() => {
    if (!establishmentId) return;

    setIsLoading(true);
    getAdminBranding(establishmentId)
      .then((data: RestaurantBranding) => {
        setFantasyName(data.fantasyName || '');
        setDescription(data.description || '');
        setPrimaryColor(data.primaryColor || '#10B981');
        setSecondaryColor(data.secondaryColor || '#065F46');
        setAccentColor(data.accentColor || '#F59E0B');
        setLogoUrl(data.logoUrl || null);
      })
      .catch((err) => {
        setErrorMessage(err instanceof Error ? err.message : 'Error al cargar la configuración de marca');
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, [establishmentId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!establishmentId) return;

    setSuccessMessage(null);
    setErrorMessage(null);

    if (!fantasyName.trim() || fantasyName.trim().length < 2) {
      setErrorMessage('El nombre de fantasía debe tener al menos 2 caracteres.');
      return;
    }

    if (!isValidHexColor(primaryColor) || !isValidHexColor(secondaryColor) || !isValidHexColor(accentColor)) {
      setErrorMessage('Todos los colores deben cumplir con el formato hexadecimal #RRGGBB.');
      return;
    }

    try {
      setIsSubmitting(true);
      const updated = await updateAdminBranding(establishmentId, {
        fantasyName: fantasyName.trim(),
        description: description.trim(),
        primaryColor,
        secondaryColor,
        accentColor,
      });

      setFantasyName(updated.fantasyName);
      setDescription(updated.description);
      setPrimaryColor(updated.primaryColor);
      setSecondaryColor(updated.secondaryColor);
      setAccentColor(updated.accentColor);
      setSuccessMessage('Branding actualizado exitosamente.');
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : 'Error al guardar la configuración de branding.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLogoUpload = async (file: File) => {
    if (!establishmentId) return;

    try {
      setIsUploadingLogo(true);
      setErrorMessage(null);
      const response = await uploadRestaurantLogo(establishmentId, file);
      setLogoUrl(response.logoUrl);
      setSuccessMessage('Logotipo subido exitosamente.');
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : 'Error al subir el logotipo.');
    } finally {
      setIsUploadingLogo(false);
    }
  };

  const primaryContrast = isValidHexColor(primaryColor) ? getOptimalContrastColor(primaryColor) : '#FFFFFF';
  const secondaryContrast = isValidHexColor(secondaryColor) ? getOptimalContrastColor(secondaryColor) : '#FFFFFF';
  const accentContrast = isValidHexColor(accentColor) ? getOptimalContrastColor(accentColor) : '#000000';

  return (
    <WorkspaceLayout
      title="Mi Local"
      subtitle="Configuración de identidad visual, logotipo y branding comercial del restaurante"
    >
      <div className="max-w-6xl mx-auto space-y-6">
        {successMessage && (
          <div className="flex items-center gap-2 p-4 bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200 rounded-lg text-sm">
            <CheckCircle2 className="w-5 h-5 flex-shrink-0 text-emerald-600" />
            <span>{successMessage}</span>
          </div>
        )}

        {errorMessage && (
          <div className="flex items-center gap-2 p-4 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-200 rounded-lg text-sm">
            <AlertCircle className="w-5 h-5 flex-shrink-0 text-red-600" />
            <span>{errorMessage}</span>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Formulario de configuración */}
          <GlassCard variant="elevated" padding="lg">
            <form onSubmit={handleSubmit} className="space-y-6">
              <div className="space-y-4">
                <h3 className="text-lg font-semibold text-[var(--ee-text)] flex items-center gap-2">
                  <Store className="w-5 h-5 text-[var(--ee-primary)]" />
                  Identidad Comercial
                </h3>

                <div>
                  <label className="block text-sm font-medium text-[var(--ee-text)] mb-1">
                    Nombre de Fantasía
                  </label>
                  <input
                    type="text"
                    value={fantasyName}
                    onChange={(e) => setFantasyName(e.target.value)}
                    placeholder="Ej. Café Central Palermo"
                    required
                    disabled={isLoading || isSubmitting}
                    className="w-full min-h-[44px] px-3 py-2 border border-[var(--ee-border)] rounded-[var(--ee-radius-sm)] text-sm bg-[var(--ee-surface)] text-[var(--ee-text)] shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
                  />
                  <p className="text-xs text-[var(--ee-text-muted)] mt-1">Nombre comercial visible para los comensales.</p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-[var(--ee-text)] mb-1">
                    Descripción Comercial
                  </label>
                  <textarea
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Ej. Cafetería de especialidad y pastelería artesanal."
                    rows={3}
                    disabled={isLoading || isSubmitting}
                    className="w-full px-3 py-2 border border-[var(--ee-border)] rounded-[var(--ee-radius-sm)] text-sm bg-[var(--ee-surface)] text-[var(--ee-text)] shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
                  />
                </div>

                <LogoUploader
                  currentLogoUrl={logoUrl}
                  onUpload={handleLogoUpload}
                  isUploading={isUploadingLogo}
                  disabled={isLoading || isSubmitting}
                />
              </div>

              <div className="pt-4 border-t border-[var(--ee-border)] space-y-4">
                <h3 className="text-lg font-semibold text-[var(--ee-text)]">
                  Tríada Cromática
                </h3>

                <ColorPickerInput
                  label="Color Primario (Botones y Acciones)"
                  value={primaryColor}
                  onChange={setPrimaryColor}
                  helperText="Utilizado en botones principales, agregados al carrito y confirmación."
                  disabled={isLoading || isSubmitting}
                />

                <ColorPickerInput
                  label="Color Secundario (Cabecera y Navegación)"
                  value={secondaryColor}
                  onChange={setSecondaryColor}
                  helperText="Utilizado en barras de navegación, encabezados y fondos destacados."
                  disabled={isLoading || isSubmitting}
                />

                <ColorPickerInput
                  label="Color de Acento (Badges e Insignias)"
                  value={accentColor}
                  onChange={setAccentColor}
                  helperText="Utilizado en etiquetas promocionales, distintivos y alertas."
                  disabled={isLoading || isSubmitting}
                />
              </div>

              <div className="pt-4 flex justify-end">
                <Button
                  type="submit"
                  variant="primary"
                  disabled={isLoading || isSubmitting}
                  loading={isSubmitting}
                  className="min-h-[44px]"
                >
                  {isSubmitting ? 'Guardando...' : 'Guardar Cambios'}
                </Button>
              </div>
            </form>
          </GlassCard>

          {/* Previsualización en vivo (aislamiento estricto de marca) */}
          <div className="space-y-4" data-brand-preview>
            <GlassCard variant="elevated" padding="lg" className="space-y-4 sticky top-6">
              <h3 className="text-lg font-semibold text-[var(--ee-text)] flex items-center gap-2">
                <Eye className="w-5 h-5 text-[var(--ee-primary)]" />
                Previsualización de Marca
              </h3>
              <p className="text-xs text-[var(--ee-text-muted)]">
                Así experimentarán los comensales los colores y elementos de marca en tu catálogo:
              </p>

              {/* Header preview */}
              <div
                className="rounded-xl p-4 shadow-sm transition-colors space-y-3"
                style={{ backgroundColor: secondaryColor, color: secondaryContrast }}
              >
                <div className="flex items-center gap-3">
                  {logoUrl && !previewImageError ? (
                    <img
                      src={resolveApiMediaUrl(logoUrl)}
                      alt="Logo local"
                      onError={() => setPreviewImageError(true)}
                      className="w-12 h-12 rounded-lg object-contain bg-white/10 p-1"
                    />
                  ) : (
                    <div className="w-12 h-12 rounded-lg bg-white/20 flex items-center justify-center font-bold text-xl">
                      {(fantasyName || 'E').charAt(0).toUpperCase()}
                    </div>
                  )}
                  <div>
                    <h4 className="font-bold text-base leading-tight">
                      {fantasyName || 'Nombre del Restaurante'}
                    </h4>
                    <p className="text-xs opacity-90 line-clamp-1">
                      {description || 'Descripción comercial del local'}
                    </p>
                  </div>
                </div>
              </div>

              {/* Elements preview */}
              <div className="p-4 border border-stone-200 dark:border-stone-700 rounded-xl space-y-4 bg-stone-50 dark:bg-stone-900/50">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-stone-600 dark:text-stone-400">
                    Badge de Menú
                  </span>
                  <span
                    className="text-xs px-2.5 py-1 rounded-full font-semibold shadow-xs"
                    style={{ backgroundColor: accentColor, color: accentContrast }}
                  >
                    Plato Recomendado
                  </span>
                </div>

                <div className="flex items-center justify-between pt-2 border-t border-stone-200 dark:border-stone-700">
                  <span className="text-xs font-medium text-stone-600 dark:text-stone-400">
                    Botón de Compra
                  </span>
                  <button
                    type="button"
                    className="px-4 py-2 rounded-lg font-semibold text-sm shadow-sm transition-all"
                    style={{ backgroundColor: primaryColor, color: primaryContrast }}
                  >
                    Pedir Ahora
                  </button>
                </div>
              </div>

              <div className="text-xs text-[var(--ee-text-muted)] text-center">
                El contraste legible WCAG 2.2 AA se recalcula automáticamente en tiempo real.
              </div>
            </GlassCard>
          </div>
        </div>
      </div>
    </WorkspaceLayout>
  );
};
