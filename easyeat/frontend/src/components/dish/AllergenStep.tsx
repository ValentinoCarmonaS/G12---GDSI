import React, { useState, useEffect } from 'react';
import { COMMON_ALLERGENS, DIETARY_LIFESTYLES } from '@/constants/allergenTaxonomy';

export interface AllergenStepProps {
  dishId?: string;
  initialRestrictions?: string[];
  initialConfirmed?: boolean;
  onSaveAllergens: (restrictionCodes: string[], confirmed: boolean) => Promise<void>;
  isReadOnly?: boolean;
  isSaving?: boolean;
}

export const AllergenStep: React.FC<AllergenStepProps> = ({
  initialRestrictions = [],
  initialConfirmed = false,
  onSaveAllergens,
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
    if (isReadOnly) return;
    setSelectedCodes((prev) => {
      const next = new Set(prev);
      if (next.has(code)) {
        next.delete(code);
      } else {
        next.add(code);
      }
      return next;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isReadOnly || isSaving) return;
    await onSaveAllergens(Array.from(selectedCodes), isConfirmed);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-8">
      {/* Informative Guidance Banner */}
      <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-sm">
        <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-100 mb-1">
          Etiquetado de Alérgenos y Preferencias Dietarias (Paso 3)
        </h2>
        <p className="text-xs text-zinc-500 dark:text-zinc-400">
          Como Chef responsable, identificá claramente qué declaraciones dietarias aplican a este plato.
          Tener cero alérgenos seleccionados es un estado válido siempre que confirmes explícitamente la sección.
        </p>
      </div>

      {/* Confirmation Status Banner */}
      <div
        className={`p-4 rounded-xl border flex items-center justify-between gap-4 ${
          isConfirmed
            ? 'bg-emerald-50 dark:bg-emerald-950/30 border-emerald-300 dark:border-emerald-800 text-emerald-900 dark:text-emerald-300'
            : 'bg-amber-50 dark:bg-amber-950/30 border-amber-300 dark:border-amber-800 text-amber-900 dark:text-amber-300'
        }`}
      >
        <div className="flex items-center gap-3">
          <span className="text-xl">{isConfirmed ? '✓' : '⚠️'}</span>
          <div>
            <div className="text-sm font-semibold">
              {isConfirmed ? 'Sección de alérgenos confirmada' : 'Confirmación pendiente'}
            </div>
            <div className="text-xs opacity-80">
              {isConfirmed
                ? 'El plato cumple con el requisito de alérgenos para el envío a revisión.'
                : 'Debés tildar la confirmación explícita para habilitar el envío a revisión.'}
            </div>
          </div>
        </div>

        <label className="flex items-center gap-2 cursor-pointer select-none">
          <input
            type="checkbox"
            id="confirm-allergens-section"
            checked={isConfirmed}
            disabled={isReadOnly}
            onChange={(e) => setIsConfirmed(e.target.checked)}
            className="w-4 h-4 rounded text-amber-500 focus:ring-amber-400 border-zinc-300 dark:border-zinc-700 disabled:opacity-50"
          />
          <span className="text-xs font-semibold">Confirmar sección de alérgenos</span>
        </label>
      </div>

      {/* Group 1: Dietary Lifestyles */}
      <div className="p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-sm">
        <div className="mb-4">
          <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
            Estilos de Vida Dietarios
          </h3>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            Declaraciones de aptitud y advertencias de gluten para comensales.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {DIETARY_LIFESTYLES.map((item) => {
            const isChecked = selectedCodes.has(item.code);
            return (
              <label
                key={item.code}
                htmlFor={`allergen-${item.code}`}
                className={`p-4 rounded-lg border transition-all cursor-pointer flex flex-col justify-between ${
                  isChecked
                    ? 'border-amber-500 bg-amber-500/10 dark:bg-amber-500/15'
                    : 'border-zinc-200 dark:border-zinc-800 hover:border-zinc-400 dark:hover:border-zinc-700'
                } ${isReadOnly ? 'opacity-60 cursor-not-allowed' : ''}`}
              >
                <div className="flex items-start justify-between gap-2 mb-2">
                  <span className="font-semibold text-sm text-zinc-900 dark:text-zinc-100">
                    {item.name}
                  </span>
                  <input
                    type="checkbox"
                    id={`allergen-${item.code}`}
                    aria-label={item.name}
                    checked={isChecked}
                    disabled={isReadOnly}
                    onChange={() => toggleCode(item.code)}
                    className="w-4 h-4 rounded text-amber-500 focus:ring-amber-400 border-zinc-300 dark:border-zinc-700 disabled:opacity-50"
                  />
                </div>
                <p className="text-xs text-zinc-500 dark:text-zinc-400 mb-3">
                  {item.description}
                </p>
                <div>
                  <span
                    className={`inline-block text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                      item.isPositive
                        ? 'bg-emerald-100 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-300 dark:border-emerald-800'
                        : 'bg-red-100 dark:bg-red-950/60 text-red-700 dark:text-red-300 border border-red-300 dark:border-red-800'
                    }`}
                  >
                    {item.warningLabel}
                  </span>
                </div>
              </label>
            );
          })}
        </div>
      </div>

      {/* Group 2: Common Allergens */}
      <div className="p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-sm">
        <div className="mb-4">
          <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
            Alérgenos Comunes
          </h3>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            Ingredientes alergénicos estandarizados que deben ser informados preventivamente al comensal.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
          {COMMON_ALLERGENS.map((item) => {
            const isChecked = selectedCodes.has(item.code);
            return (
              <label
                key={item.code}
                htmlFor={`allergen-${item.code}`}
                className={`p-3.5 rounded-lg border transition-all cursor-pointer flex flex-col justify-between ${
                  isChecked
                    ? 'border-amber-500 bg-amber-500/10 dark:bg-amber-500/15'
                    : 'border-zinc-200 dark:border-zinc-800 hover:border-zinc-400 dark:hover:border-zinc-700'
                } ${isReadOnly ? 'opacity-60 cursor-not-allowed' : ''}`}
              >
                <div className="flex items-start justify-between gap-2 mb-1.5">
                  <span className="font-semibold text-sm text-zinc-900 dark:text-zinc-100">
                    {item.name}
                  </span>
                  <input
                    type="checkbox"
                    id={`allergen-${item.code}`}
                    aria-label={item.name}
                    checked={isChecked}
                    disabled={isReadOnly}
                    onChange={() => toggleCode(item.code)}
                    className="w-4 h-4 rounded text-amber-500 focus:ring-amber-400 border-zinc-300 dark:border-zinc-700 disabled:opacity-50"
                  />
                </div>
                <p className="text-xs text-zinc-500 dark:text-zinc-400 mb-2">
                  {item.description}
                </p>
                <div>
                  <span className="inline-block text-[10px] font-semibold px-2 py-0.5 rounded-full bg-amber-100 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 border border-amber-300 dark:border-amber-800">
                    {item.warningLabel}
                  </span>
                </div>
              </label>
            );
          })}
        </div>
      </div>

      {/* Save Action Footer */}
      <div className="pt-2 flex justify-end">
        <button
          type="submit"
          disabled={isReadOnly || isSaving}
          className="px-6 py-2.5 bg-amber-500 hover:bg-amber-600 text-zinc-950 font-semibold text-sm rounded-lg shadow-sm transition focus:outline-none focus:ring-2 focus:ring-amber-400 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isSaving ? 'Guardando alérgenos...' : 'Guardar y Confirmar Alérgenos'}
        </button>
      </div>
    </form>
  );
};

export default AllergenStep;
