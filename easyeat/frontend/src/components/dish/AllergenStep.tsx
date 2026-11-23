import React, { useState, useEffect } from 'react';
import { COMMON_ALLERGENS, DIETARY_LIFESTYLES } from '@/constants/allergenTaxonomy';
import { BrutalistCard } from '@/components/chef/BrutalistCard';

export interface AllergenStepProps {
  dishId?: string;
  initialRestrictions?: string[];
  initialConfirmed?: boolean;
  onSaveAllergens?: (restrictionCodes: string[], confirmed: boolean) => Promise<void>;
  onPrev?: () => void;
  onNext?: (restrictionCodes: string[], confirmed: boolean) => void;
  onDraftChange?: (restrictionCodes: string[], confirmed: boolean) => void;
  isReadOnly?: boolean;
  isSaving?: boolean;
}

export const AllergenStep: React.FC<AllergenStepProps> = ({
  initialRestrictions = [],
  initialConfirmed = false,
  onSaveAllergens,
  onPrev,
  onNext,
  onDraftChange,
  isReadOnly = false,
  isSaving = false,
}) => {
  const [selectedCodes, setSelectedCodes] = useState<Set<string>>(new Set(initialRestrictions));
  const [isConfirmed, setIsConfirmed] = useState<boolean>(initialConfirmed);

  useEffect(() => {
    setSelectedCodes(new Set(initialRestrictions));
    setIsConfirmed(initialConfirmed);
  }, [initialRestrictions, initialConfirmed]);

  const toggleCode = (code: string) => {
    if (isReadOnly || isSaving) return;
    setSelectedCodes((prev) => {
      const next = new Set(prev);
      if (next.has(code)) {
        next.delete(code);
      } else {
        next.add(code);
      }
      onDraftChange?.(Array.from(next), isConfirmed);
      return next;
    });
  };

  const handleConfirmChange = (checked: boolean) => {
    if (isReadOnly || isSaving) return;
    setIsConfirmed(checked);
    onDraftChange?.(Array.from(selectedCodes), checked);
  };

  const handleContinue = async () => {
    const codes = Array.from(selectedCodes);
    if (onNext) {
      onNext(codes, isConfirmed);
      return;
    }
    if (onSaveAllergens) {
      await onSaveAllergens(codes, isConfirmed);
    }
  };

  return (
    <BrutalistCard className="p-6 space-y-6">
      <div>
        <h2 className="text-lg font-extrabold uppercase tracking-wide text-[#0A0A0A]">
          Alérgenos y restricciones alimentarias
        </h2>
        <p className="text-xs font-mono text-[#52525B]">
          Marcá todos los alérgenos presentes o incompatibilidades del plato.
        </p>
      </div>

      <section className="space-y-3">
        <h3 className="text-xs font-bold uppercase tracking-wider text-[#0A0A0A]">
          Alérgenos comunes
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {COMMON_ALLERGENS.map((item) => {
            const checked = selectedCodes.has(item.code);
            return (
              <label
                key={item.code}
                className={`min-h-[48px] p-3 border-2 border-[#0A0A0A] flex items-center gap-3 cursor-pointer select-none transition-colors ${
                  checked ? 'bg-[#0A0A0A] text-[#FFFFFF]' : 'bg-[#FFFFFF] text-[#0A0A0A] hover:bg-[#F5F5F4]'
                } ${isReadOnly ? 'opacity-75 cursor-not-allowed' : ''}`}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={isReadOnly || isSaving}
                  onChange={() => toggleCode(item.code)}
                  className="h-5 w-5 accent-[#009866]"
                />
                <span className="text-sm font-bold">{item.label}</span>
              </label>
            );
          })}
        </div>
      </section>

      <section className="space-y-3">
        <h3 className="text-xs font-bold uppercase tracking-wider text-[#0A0A0A]">
          Estilos y preferencias
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {DIETARY_LIFESTYLES.map((item) => {
            const checked = selectedCodes.has(item.code);
            return (
              <label
                key={item.code}
                className={`min-h-[48px] p-3 border-2 border-[#0A0A0A] flex items-center gap-3 cursor-pointer select-none transition-colors ${
                  checked ? 'bg-[#0A0A0A] text-[#FFFFFF]' : 'bg-[#FFFFFF] text-[#0A0A0A] hover:bg-[#F5F5F4]'
                } ${isReadOnly ? 'opacity-75 cursor-not-allowed' : ''}`}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={isReadOnly || isSaving}
                  onChange={() => toggleCode(item.code)}
                  className="h-5 w-5 accent-[#009866]"
                />
                <span className="text-sm font-bold">{item.label}</span>
              </label>
            );
          })}
        </div>
      </section>

      <div className="border-2 border-[#0A0A0A] bg-[#F5F5F4] p-4">
        <label className="flex items-start gap-3 cursor-pointer">
          <input
            type="checkbox"
            checked={isConfirmed}
            disabled={isReadOnly || isSaving}
            onChange={(e) => handleConfirmChange(e.target.checked)}
            className="mt-0.5 h-5 w-5 accent-[#009866]"
          />
          <div>
            <span className="block text-sm font-extrabold uppercase text-[#0A0A0A]">
              Confirmar sección de alérgenos
            </span>
            <span className="block text-xs font-mono text-[#52525B]">
              Declaro haber verificado la presencia o ausencia de alérgenos en la receta.
            </span>
          </div>
        </label>
      </div>

      <div className="flex items-center justify-between pt-4 border-t-2 border-[#0A0A0A]">
        {onPrev ? (
          <button
            type="button"
            onClick={onPrev}
            disabled={isSaving}
            className="min-h-[48px] px-5 py-2.5 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] text-sm font-bold uppercase tracking-wide hover:bg-[#F5F5F4]"
          >
            Anterior
          </button>
        ) : (
          <span />
        )}
        <button
          type="button"
          onClick={handleContinue}
          disabled={isSaving}
          className="min-h-[48px] px-6 py-2.5 border-2 border-[#0A0A0A] bg-[#0A0A0A] text-[#FFFFFF] text-sm font-bold uppercase tracking-wide hover:bg-[#27272A] disabled:opacity-50"
        >
          {onNext ? 'Siguiente' : isSaving ? 'Guardando...' : 'Guardar alérgenos'}
        </button>
      </div>
    </BrutalistCard>
  );
};

export default AllergenStep;
