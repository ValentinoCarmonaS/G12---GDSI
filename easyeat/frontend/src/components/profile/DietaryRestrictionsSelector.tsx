import React, { useState, useEffect } from 'react';
import type { DietaryRestrictionItem } from '../../types/profile';
import { AlertCircle, AlertTriangle, Check, Loader2, Utensils, ShieldAlert } from 'lucide-react';

export interface DietaryRestrictionsSelectorProps {
  availableTaxonomy: DietaryRestrictionItem[];
  selectedCodes: string[];
  onSave: (codes: string[]) => Promise<void>;
  loading?: boolean;
}

export const DietaryRestrictionsSelector: React.FC<DietaryRestrictionsSelectorProps> = ({
  availableTaxonomy,
  selectedCodes,
  onSave,
  loading = false,
}) => {
  const [currentSelected, setCurrentSelected] = useState<string[]>(selectedCodes);
  const [saving, setSaving] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    setCurrentSelected(selectedCodes);
  }, [selectedCodes]);

  const lifestyles = availableTaxonomy.filter(
    (item) => item.category === 'DIETARY_LIFESTYLE'
  );
  const allergens = availableTaxonomy.filter(
    (item) => item.category === 'COMMON_ALLERGEN'
  );

  const handleToggle = (code: string) => {
    setErrorMessage(null);
    setCurrentSelected((prev) =>
      prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]
    );
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setSaving(true);
    try {
      await onSave(currentSelected);
    } catch (err: any) {
      const msg =
        err?.response?.data?.detail ||
        err?.message ||
        'No se pudieron guardar las preferencias';
      setErrorMessage(msg);
      // Revert to persisted selectedCodes to guarantee 0% optimistic state on failure (FR-014)
      setCurrentSelected(selectedCodes);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-neutral-200/80 shadow-sm p-6 sm:p-8">
      <div className="flex items-center gap-3 pb-6 border-b border-neutral-100">
        <div className="w-10 h-10 rounded-xl bg-amber-50 flex items-center justify-center text-amber-700">
          <Utensils className="w-5 h-5" />
        </div>
        <div>
          <h2 className="text-lg font-bold text-neutral-900 tracking-tight">
            Restricciones alimentarias y preferencias
          </h2>
          <p className="text-sm text-neutral-600">
            Marcá tus necesidades dietéticas para filtrar automáticamente los platos del catálogo y proteger tu salud.
          </p>
        </div>
      </div>

      {errorMessage && (
        <div
          role="alert"
          className="mt-6 p-4 rounded-xl bg-red-50 border border-red-200 text-red-800 text-sm flex items-start gap-3 animate-fade-in"
        >
          <AlertCircle className="w-5 h-5 shrink-0 text-red-600 mt-0.5" />
          <div>
            <p className="font-semibold">Error al guardar</p>
            <p>{errorMessage}</p>
          </div>
        </div>
      )}

      {/* Advisory Notification for in-flight kitchen orders (FR-016) */}
      <div className="mt-6 p-4 rounded-xl bg-amber-50/70 border border-amber-200 text-amber-900 text-xs sm:text-sm flex items-start gap-3">
        <AlertTriangle className="w-5 h-5 shrink-0 text-amber-700 mt-0.5" />
        <p className="leading-relaxed">
          <strong className="font-semibold">Aviso sobre pedidos:</strong> Los cambios se aplican de forma inmediata al carrito activo y pedidos futuros. Si tenés pedidos en preparación en la cocina del restaurante, la comanda no se modifica automáticamente; contactá al restaurante directamente.
        </p>
      </div>

      <form onSubmit={handleSave} className="mt-8 space-y-8">
        {/* Group 1: Dietary Lifestyles */}
        <div>
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
            <h3 className="text-sm font-bold uppercase tracking-wider text-neutral-700">
              Estilos de vida
            </h3>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {lifestyles.map((item) => {
              const isChecked = currentSelected.includes(item.code);
              return (
                <label
                  key={item.code}
                  htmlFor={`dietary-${item.code}`}
                  className={`relative flex items-start p-4 rounded-xl border cursor-pointer transition-all ${
                    isChecked
                      ? 'border-emerald-600 bg-emerald-50/40 shadow-xs'
                      : 'border-neutral-200 hover:border-neutral-300 bg-neutral-50/40'
                  }`}
                >
                  <div className="flex items-center h-5">
                    <input
                      id={`dietary-${item.code}`}
                      type="checkbox"
                      checked={isChecked}
                      onChange={() => handleToggle(item.code)}
                      className="sr-only"
                    />
                    <div
                      className={`w-5 h-5 rounded-md flex items-center justify-center border transition-all ${
                        isChecked
                          ? 'bg-emerald-600 border-emerald-600 text-white'
                          : 'border-neutral-300 bg-white'
                      }`}
                    >
                      {isChecked && <Check className="w-3.5 h-3.5 stroke-[3]" />}
                    </div>
                  </div>
                  <div className="ml-3 text-sm">
                    <span className="font-semibold text-neutral-900 block">
                      {item.name}
                    </span>
                    {item.description && (
                      <span className="text-xs text-neutral-500 block mt-0.5">
                        {item.description}
                      </span>
                    )}
                  </div>
                </label>
              );
            })}
          </div>
        </div>

        {/* Group 2: Common Allergens */}
        <div>
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
            <h3 className="text-sm font-bold uppercase tracking-wider text-neutral-700">
              Alérgenos comunes
            </h3>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
            {allergens.map((item) => {
              const isChecked = currentSelected.includes(item.code);
              return (
                <label
                  key={item.code}
                  htmlFor={`dietary-${item.code}`}
                  className={`relative flex items-start p-4 rounded-xl border cursor-pointer transition-all ${
                    isChecked
                      ? 'border-rose-600 bg-rose-50/40 shadow-xs'
                      : 'border-neutral-200 hover:border-neutral-300 bg-neutral-50/40'
                  }`}
                >
                  <div className="flex items-center h-5">
                    <input
                      id={`dietary-${item.code}`}
                      type="checkbox"
                      checked={isChecked}
                      onChange={() => handleToggle(item.code)}
                      className="sr-only"
                    />
                    <div
                      className={`w-5 h-5 rounded-md flex items-center justify-center border transition-all ${
                        isChecked
                          ? 'bg-rose-600 border-rose-600 text-white'
                          : 'border-neutral-300 bg-white'
                      }`}
                    >
                      {isChecked && <Check className="w-3.5 h-3.5 stroke-[3]" />}
                    </div>
                  </div>
                  <div className="ml-3 text-sm">
                    <span className="font-semibold text-neutral-900 block">
                      {item.name}
                    </span>
                    {item.description && (
                      <span className="text-xs text-neutral-500 block mt-0.5">
                        {item.description}
                      </span>
                    )}
                  </div>
                </label>
              );
            })}
          </div>
        </div>

        <div className="flex justify-end pt-4 border-t border-neutral-100">
          <button
            type="submit"
            disabled={saving || loading}
            className="inline-flex items-center justify-center px-6 py-2.5 rounded-xl font-medium text-sm text-white bg-neutral-900 hover:bg-neutral-800 disabled:opacity-50 disabled:cursor-not-allowed transition shadow-sm"
          >
            {saving ? (
              <>
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                Guardando preferencias...
              </>
            ) : (
              'Guardar preferencias'
            )}
          </button>
        </div>
      </form>
    </div>
  );
};

export default DietaryRestrictionsSelector;
