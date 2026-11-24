import React from 'react';
import type { DishDetailResponse } from '@/types/dish';
import type { DishEditorTab } from './DishStepper';
import { BrutalistCard } from '@/components/chef/BrutalistCard';

export interface ReviewSubmitStepProps {
  dish: DishDetailResponse;
  onSubmitForReview: () => void | Promise<void>;
  onSaveDraft?: () => void | Promise<void>;
  onPrev?: () => void;
  onNavigateStep?: (tab: DishEditorTab) => void;
  isSubmitting?: boolean;
  isReadOnly?: boolean;
}

export const ReviewSubmitStep: React.FC<ReviewSubmitStepProps> = ({
  dish,
  onSubmitForReview,
  onSaveDraft,
  onPrev,
  onNavigateStep,
  isSubmitting = false,
  isReadOnly = false,
}) => {
  const isIdentityComplete = Boolean(dish.name && dish.name.trim().length > 0 && dish.category);

  const isRecipeComplete = Boolean(
    dish.recipe &&
      dish.recipe.length > 0 &&
      dish.recipe.every(
        (line) => line.quantity > 0 && line.isIngredientActive !== false
      )
  );

  const isAllergensComplete = Boolean(dish.allergensConfirmed);

  const isComplete =
    dish.isCompleteForReview ??
    (isIdentityComplete && isRecipeComplete && isAllergensComplete);

  const isPendingPrice = dish.status === 'PENDING_PRICE';
  const effectiveReadOnly =
    isReadOnly || isPendingPrice || dish.status === 'PUBLISHED' || dish.status === 'CANCELLED';

  const checklist: {
    id: DishEditorTab;
    label: string;
    complete: boolean;
    detail: string;
  }[] = [
    {
      id: 'identity',
      label: '01. Identidad del plato',
      complete: isIdentityComplete,
      detail: isIdentityComplete ? `${dish.name} (${dish.category})` : 'Falta nombre o categoría',
    },
    {
      id: 'recipe',
      label: '02. Receta (BOM)',
      complete: isRecipeComplete,
      detail: isRecipeComplete
        ? `${dish.recipe.length} ingrediente(s) cargados`
        : 'Agregá al menos 1 ingrediente con cantidad > 0',
    },
    {
      id: 'allergens',
      label: '03. Alérgenos',
      complete: isAllergensComplete,
      detail: isAllergensComplete
        ? 'Sección confirmada'
        : 'Confirmá la sección de alérgenos',
    },
  ];

  return (
    <BrutalistCard className="p-6 space-y-6">
      <div>
        <h2 className="text-lg font-extrabold uppercase tracking-wide text-[#0A0A0A]">
          Revisión y envío
        </h2>
        <p className="text-xs font-mono text-[#52525B]">
          Verificá que todos los pasos estén completos antes de enviar a revisión.
        </p>
      </div>

      {effectiveReadOnly && (
        <div
          role="status"
          className="border-2 border-[#0A0A0A] bg-[#FEF3C7] text-[#0A0A0A] p-4 font-bold text-sm"
        >
          Solo lectura — Este plato se encuentra en estado {dish.status} y no puede editarse.
        </div>
      )}

      <div className="space-y-3">
        {checklist.map((item) => (
          <div
            key={item.id}
            className="border-2 border-[#0A0A0A] bg-[#FFFFFF] p-4 flex flex-wrap items-center justify-between gap-3"
          >
            <div>
              <p className="font-bold text-sm text-[#0A0A0A]">{item.label}</p>
              <p className="font-mono text-xs text-[#52525B]">{item.detail}</p>
            </div>
            <div className="flex items-center gap-2">
              <span
                className={`font-mono text-xs font-bold px-2 py-1 border-2 border-[#0A0A0A] ${
                  item.complete
                    ? 'bg-[#009866] text-[#0A0A0A]'
                    : 'bg-[#FEE2E2] text-[#DC2626]'
                }`}
              >
                {item.complete ? 'COMPLETO' : 'PENDIENTE'}
              </span>
              {!item.complete && onNavigateStep && !effectiveReadOnly && (
                <button
                  type="button"
                  onClick={() => onNavigateStep(item.id)}
                  className="px-3 py-1 border-2 border-[#0A0A0A] bg-[#F5F5F4] text-[#0A0A0A] text-xs font-bold uppercase hover:bg-[#0A0A0A] hover:text-[#FFFFFF]"
                >
                  Ir al paso
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 pt-4 border-t-2 border-[#0A0A0A]">
        {onPrev ? (
          <button
            type="button"
            onClick={onPrev}
            disabled={isSubmitting}
            className="min-h-[48px] px-5 py-2.5 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] text-sm font-bold uppercase tracking-wide hover:bg-[#F5F5F4]"
          >
            Anterior
          </button>
        ) : (
          <span />
        )}

        {!effectiveReadOnly && (
          <div className="flex flex-wrap items-center gap-3">
            {onSaveDraft && (
              <button
                type="button"
                onClick={onSaveDraft}
                disabled={isSubmitting}
                className="min-h-[48px] px-5 py-2.5 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] text-sm font-bold uppercase tracking-wide hover:bg-[#F5F5F4] disabled:opacity-50"
              >
                {isSubmitting ? 'Guardando...' : 'Guardar borrador'}
              </button>
            )}
            <button
              type="button"
              onClick={onSubmitForReview}
              disabled={isSubmitting || !isComplete}
              className="min-h-[48px] px-6 py-2.5 border-2 border-[#0A0A0A] bg-[#009866] text-[#0A0A0A] text-sm font-extrabold uppercase tracking-wide hover:bg-[#007A52] hover:text-[#FFFFFF] disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? 'Enviando...' : 'Guardar y enviar a revisión'}
            </button>
          </div>
        )}
      </div>
    </BrutalistCard>
  );
};

export default ReviewSubmitStep;
