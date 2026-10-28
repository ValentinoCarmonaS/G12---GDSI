import React from 'react';
import type { DishDetailResponse } from '@/types/dish';

export interface ReviewSubmitStepProps {
  dish: DishDetailResponse;
  onSubmitForReview: () => void | Promise<void>;
  isSubmitting?: boolean;
  isReadOnly?: boolean;
}

export const ReviewSubmitStep: React.FC<ReviewSubmitStepProps> = ({
  dish,
  onSubmitForReview,
  isSubmitting = false,
  isReadOnly = false,
}) => {
  // Section 1: Identidad básica
  const isIdentityComplete = Boolean(dish.name && dish.name.trim().length > 0 && dish.category);

  // Section 2: Receta y escandallo
  const isRecipeComplete = Boolean(
    dish.recipe &&
      dish.recipe.length > 0 &&
      dish.recipe.every(
        (line) => line.quantity > 0 && line.isIngredientActive !== false
      )
  );

  // Section 3: Alérgenos y preferencias
  const isAllergensComplete = Boolean(dish.allergensConfirmed);

  // Overall completeness
  const isComplete =
    dish.isCompleteForReview ??
    (isIdentityComplete && isRecipeComplete && isAllergensComplete);

  const isPendingPrice = dish.status === 'PENDING_PRICE';
  const effectiveReadOnly = isReadOnly || isPendingPrice;

  return (
    <div className="space-y-6">
      {/* Locked banner if PENDING_PRICE */}
      {isPendingPrice && (
        <div
          data-testid="locked-dish-banner"
          className="p-4 rounded-xl border border-amber-300 dark:border-amber-700/60 bg-amber-50 dark:bg-amber-950/40 text-amber-900 dark:text-amber-200 flex items-start gap-3 shadow-sm"
        >
          <div className="p-1 rounded-full bg-amber-200 dark:bg-amber-900 text-amber-800 dark:text-amber-300">
            <svg
              className="w-5 h-5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
              />
            </svg>
          </div>
          <div>
            <h4 className="font-semibold text-sm">Ficha técnica en revisión</h4>
            <p className="text-xs mt-1 text-amber-800 dark:text-amber-300">
              Esta ficha técnica ha sido enviada al Administrador para revisión de precios y se
              encuentra bloqueada para modificaciones.
            </p>
          </div>
        </div>
      )}

      {/* Header and description */}
      <div className="p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-sm">
        <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-100 mb-1">
          Paso 4: Revisión y Envío a Administrador
        </h2>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          Revisá el estado de los componentes de la ficha técnica. Una vez enviada, el plato pasará a
          estado <strong>Pendiente de Precio (PENDING_PRICE)</strong> y no podrá ser modificado hasta
          que el Administrador asigne el precio de venta.
        </p>
      </div>

      {/* Checklist section */}
      <div className="p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-sm space-y-4">
        <h3 className="text-sm font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
          Lista de verificación de completitud
        </h3>

        <div className="space-y-3">
          {/* Item 1: Identidad Básica */}
          <div className="flex items-center justify-between p-3 rounded-lg border border-zinc-100 dark:border-zinc-800/80 bg-zinc-50/50 dark:bg-zinc-800/30">
            <div className="flex items-center gap-3">
              <span
                className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
                  isIdentityComplete
                    ? 'bg-emerald-100 dark:bg-emerald-950/80 text-emerald-700 dark:text-emerald-300'
                    : 'bg-red-100 dark:bg-red-950/80 text-red-700 dark:text-red-300'
                }`}
              >
                {isIdentityComplete ? '✓' : '!'}
              </span>
              <div>
                <span className="font-medium text-sm text-zinc-900 dark:text-zinc-100">
                  Identidad Básica
                </span>
                <p className="text-xs text-zinc-500 dark:text-zinc-400">
                  {isIdentityComplete
                    ? `${dish.name} (${dish.category})`
                    : 'Nombre y categoría obligatorios'}
                </p>
              </div>
            </div>
            <span
              className={`text-xs font-semibold px-2.5 py-1 rounded-full ${
                isIdentityComplete
                  ? 'bg-emerald-100 dark:bg-emerald-950 text-emerald-700 dark:text-emerald-300'
                  : 'bg-red-100 dark:bg-red-950 text-red-700 dark:text-red-300'
              }`}
            >
              {isIdentityComplete ? 'Completo' : 'Incompleto'}
            </span>
          </div>

          {/* Item 2: Receta y Escandallo */}
          <div className="flex items-center justify-between p-3 rounded-lg border border-zinc-100 dark:border-zinc-800/80 bg-zinc-50/50 dark:bg-zinc-800/30">
            <div className="flex items-center gap-3">
              <span
                className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
                  isRecipeComplete
                    ? 'bg-emerald-100 dark:bg-emerald-950/80 text-emerald-700 dark:text-emerald-300'
                    : 'bg-red-100 dark:bg-red-950/80 text-red-700 dark:text-red-300'
                }`}
              >
                {isRecipeComplete ? '✓' : '!'}
              </span>
              <div>
                <span className="font-medium text-sm text-zinc-900 dark:text-zinc-100">
                  Receta y Escandallo
                </span>
                <p className="text-xs text-zinc-500 dark:text-zinc-400">
                  {isRecipeComplete
                    ? `${dish.recipe?.length || 0} ingredientes registrados (todos activos)`
                    : 'Al menos un ingrediente activo con cantidad mayor a cero'}
                </p>
              </div>
            </div>
            <span
              className={`text-xs font-semibold px-2.5 py-1 rounded-full ${
                isRecipeComplete
                  ? 'bg-emerald-100 dark:bg-emerald-950 text-emerald-700 dark:text-emerald-300'
                  : 'bg-red-100 dark:bg-red-950 text-red-700 dark:text-red-300'
              }`}
            >
              {isRecipeComplete ? 'Completo' : 'Incompleto'}
            </span>
          </div>

          {/* Item 3: Alérgenos y Preferencias */}
          <div className="flex items-center justify-between p-3 rounded-lg border border-zinc-100 dark:border-zinc-800/80 bg-zinc-50/50 dark:bg-zinc-800/30">
            <div className="flex items-center gap-3">
              <span
                className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
                  isAllergensComplete
                    ? 'bg-emerald-100 dark:bg-emerald-950/80 text-emerald-700 dark:text-emerald-300'
                    : 'bg-red-100 dark:bg-red-950/80 text-red-700 dark:text-red-300'
                }`}
              >
                {isAllergensComplete ? '✓' : '!'}
              </span>
              <div>
                <span className="font-medium text-sm text-zinc-900 dark:text-zinc-100">
                  Alérgenos y Preferencias
                </span>
                <p className="text-xs text-zinc-500 dark:text-zinc-400">
                  {isAllergensComplete
                    ? 'Declaración de alérgenos confirmada'
                    : 'Sección pendiente de confirmación explícita'}
                </p>
              </div>
            </div>
            <span
              className={`text-xs font-semibold px-2.5 py-1 rounded-full ${
                isAllergensComplete
                  ? 'bg-emerald-100 dark:bg-emerald-950 text-emerald-700 dark:text-emerald-300'
                  : 'bg-red-100 dark:bg-red-950 text-red-700 dark:text-red-300'
              }`}
            >
              {isAllergensComplete ? 'Confirmado' : 'Pendiente'}
            </span>
          </div>
        </div>

        {/* Warning cue if not complete and not already pending price */}
        {!isComplete && !isPendingPrice && (
          <div
            role="alert"
            className="p-3 rounded-lg border border-amber-200 dark:border-amber-900/60 bg-amber-50/80 dark:bg-amber-950/30 text-amber-800 dark:text-amber-300 text-xs flex items-center gap-2"
          >
            <span className="font-bold">⚠️</span>
            <span>
              Faltan requisitos para enviar a revisión. Por favor completá las secciones indicadas para
              poder enviar la ficha técnica al Administrador.
            </span>
          </div>
        )}
      </div>

      {/* Action footer */}
      <div className="flex items-center justify-end gap-3 pt-4 border-t border-zinc-200 dark:border-zinc-800">
        <button
          type="button"
          onClick={onSubmitForReview}
          disabled={!isComplete || effectiveReadOnly || isSubmitting}
          className={`px-5 py-2.5 rounded-lg text-sm font-semibold transition-all flex items-center gap-2 ${
            isPendingPrice
              ? 'bg-zinc-200 dark:bg-zinc-800 text-zinc-500 dark:text-zinc-400 cursor-not-allowed'
              : !isComplete || isSubmitting
              ? 'bg-zinc-200 dark:bg-zinc-800 text-zinc-400 dark:text-zinc-600 cursor-not-allowed'
              : 'bg-amber-500 hover:bg-amber-600 active:bg-amber-700 text-zinc-950 shadow-sm hover:shadow'
          }`}
        >
          {isSubmitting ? (
            <span>Enviando...</span>
          ) : isPendingPrice ? (
            <span>Enviado a revisión</span>
          ) : (
            <span>Enviar a revisión</span>
          )}
        </button>
      </div>
    </div>
  );
};

export default ReviewSubmitStep;
