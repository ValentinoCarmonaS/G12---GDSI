import React, { useState, useEffect } from 'react';
import { IngredientSelectorModal } from './IngredientSelectorModal';
import type { RecipeIngredientItem } from '@/types/dish';
import type { ChefIngredientItemResponse } from '@/types/inventory';
import { BrutalistCard } from '@/components/chef/BrutalistCard';
import { FeedbackState } from '@/components/chef/FeedbackState';

export interface RecipeBOMStepProps {
  dishId?: string;
  initialRecipe?: RecipeIngredientItem[];
  onSaveRecipe?: (recipe: RecipeIngredientItem[]) => void | Promise<void>;
  onPrev?: () => void;
  onNext?: (recipe: RecipeIngredientItem[]) => void;
  onDraftChange?: (recipe: RecipeIngredientItem[]) => void;
  onRemoveIngredient?: (ingredientId: string) => void | Promise<void>;
  isReadOnly?: boolean;
  isSaving?: boolean;
}

export const RecipeBOMStep: React.FC<RecipeBOMStepProps> = ({
  initialRecipe = [],
  onSaveRecipe,
  onPrev,
  onNext,
  onDraftChange,
  onRemoveIngredient,
  isReadOnly = false,
  isSaving = false,
}) => {
  const [ingredients, setIngredients] = useState<RecipeIngredientItem[]>(initialRecipe);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [duplicateWarning, setDuplicateWarning] = useState<string | null>(null);
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});
  const [rawQuantities, setRawQuantities] = useState<Record<string, string>>({});

  useEffect(() => {
    if (initialRecipe) {
      setIngredients(initialRecipe);
      const initialMap: Record<string, string> = {};
      initialRecipe.forEach((item) => {
        initialMap[item.ingredientId] = item.quantity.toString();
      });
      setRawQuantities(initialMap);
    }
  }, [initialRecipe]);

  const handleOpenModal = () => {
    setDuplicateWarning(null);
    setIsModalOpen(true);
  };

  const handleSelectIngredient = (ing: ChefIngredientItemResponse) => {
    const exists = ingredients.some((item) => item.ingredientId === ing.id);
    if (exists) {
      setDuplicateWarning(`El ingrediente "${ing.name}" ya está en la receta.`);
      setIsModalOpen(false);
      return;
    }

    const defaultQty = ing.unit === 'u' ? 1 : 0.1;
    const next = [
      ...ingredients,
      {
        ingredientId: ing.id,
        ingredientName: ing.name,
        quantity: defaultQty,
        unit: ing.unit,
      },
    ];
    setIngredients(next);
    setRawQuantities((prev) => ({ ...prev, [ing.id]: defaultQty.toString() }));
    setIsModalOpen(false);
    setDuplicateWarning(null);
    onDraftChange?.(next);
  };

  const handleQuantityChange = (ingredientId: string, value: string, unit: string) => {
    setRawQuantities((prev) => ({ ...prev, [ingredientId]: value }));

    const parsed = Number(value);
    if (!value.trim() || Number.isNaN(parsed) || parsed <= 0) {
      setValidationErrors((prev) => ({
        ...prev,
        [ingredientId]: 'La cantidad debe ser mayor a 0.',
      }));
      return;
    }

    if (unit === 'u' && !Number.isInteger(parsed)) {
      setValidationErrors((prev) => ({
        ...prev,
        [ingredientId]: 'Para unidad "u", ingresá un número entero.',
      }));
      return;
    }

    setValidationErrors((prev) => {
      const copy = { ...prev };
      delete copy[ingredientId];
      return copy;
    });

    const next = ingredients.map((item) =>
      item.ingredientId === ingredientId ? { ...item, quantity: parsed } : item
    );
    setIngredients(next);
    onDraftChange?.(next);
  };

  const handleRemove = async (ingredientId: string) => {
    const next = ingredients.filter((item) => item.ingredientId !== ingredientId);
    setIngredients(next);
    setValidationErrors((prev) => {
      const copy = { ...prev };
      delete copy[ingredientId];
      return copy;
    });
    onDraftChange?.(next);
    if (onRemoveIngredient) {
      await onRemoveIngredient(ingredientId);
    }
  };

  const validateAll = (): boolean => {
    const errors: Record<string, string> = {};
    ingredients.forEach((item) => {
      const raw = rawQuantities[item.ingredientId] ?? item.quantity.toString();
      const parsed = Number(raw);
      if (!raw.trim() || Number.isNaN(parsed) || parsed <= 0) {
        errors[item.ingredientId] = 'La cantidad debe ser mayor a 0.';
      } else if (item.unit === 'u' && !Number.isInteger(parsed)) {
        errors[item.ingredientId] = 'Para unidad "u", ingresá un número entero.';
      }
    });
    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleContinue = async () => {
    if (!validateAll()) return;
    if (onNext) {
      onNext(ingredients);
      return;
    }
    if (onSaveRecipe) {
      await onSaveRecipe(ingredients);
    }
  };

  return (
    <BrutalistCard className="p-6 space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-extrabold uppercase tracking-wide text-[#0A0A0A]">
            Ingredientes de la receta
          </h2>
          <p className="text-xs font-mono text-[#52525B]">
            Definí los insumos y cantidades exactas por porción.
          </p>
        </div>
        {!isReadOnly && (
          <button
            type="button"
            onClick={handleOpenModal}
            disabled={isSaving}
            className="min-h-[44px] px-4 py-2 border-2 border-[#0A0A0A] bg-[#0A0A0A] text-[#FFFFFF] text-xs font-bold uppercase tracking-wide hover:bg-[#27272A]"
          >
            + Agregar ingrediente
          </button>
        )}
      </div>

      {duplicateWarning && (
        <FeedbackState variant="warning" title="Ingrediente duplicado" message={duplicateWarning} />
      )}

      {ingredients.length === 0 ? (
        <div className="border-2 border-dashed border-[#0A0A0A] bg-[#F5F5F4] p-8 text-center">
          <p className="text-sm font-bold text-[#0A0A0A]">
            Todavía no agregaste ingredientes a esta receta.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {ingredients.map((item) => {
            const error = validationErrors[item.ingredientId];
            return (
              <BrutalistCard key={item.ingredientId} className="p-4 space-y-3">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-bold text-sm text-[#0A0A0A]">{item.ingredientName}</p>
                    <p className="font-mono text-xs text-[#52525B]">Unidad: {item.unit}</p>
                  </div>
                  {!isReadOnly && (
                    <button
                      type="button"
                      onClick={() => handleRemove(item.ingredientId)}
                      disabled={isSaving}
                      aria-label={`Quitar ${item.ingredientName}`}
                      className="px-2.5 py-1 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#DC2626] text-xs font-bold uppercase hover:bg-[#FEE2E2]"
                    >
                      Quitar
                    </button>
                  )}
                </div>

                <div>
                  <label
                    htmlFor={`qty-${item.ingredientId}`}
                    className="block text-xs font-bold uppercase text-[#0A0A0A] mb-1"
                  >
                    Cantidad ({item.unit})
                  </label>
                  <input
                    id={`qty-${item.ingredientId}`}
                    type="number"
                    step={item.unit === 'u' ? '1' : '0.01'}
                    min="0"
                    value={rawQuantities[item.ingredientId] ?? item.quantity.toString()}
                    onChange={(e) =>
                      handleQuantityChange(item.ingredientId, e.target.value, item.unit)
                    }
                    disabled={isReadOnly || isSaving}
                    className="w-full px-3 py-2 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] font-mono text-sm focus:outline-none focus:ring-2 focus:ring-[#0A0A0A] disabled:bg-[#F5F5F4]"
                  />
                  {error && (
                    <p role="alert" className="mt-1 text-xs font-bold text-[#DC2626]">
                      {error}
                    </p>
                  )}
                </div>
              </BrutalistCard>
            );
          })}
        </div>
      )}

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
          {onNext ? 'Siguiente' : isSaving ? 'Guardando...' : 'Guardar receta'}
        </button>
      </div>

      <IngredientSelectorModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSelectIngredient={handleSelectIngredient}
        selectedIngredientIds={ingredients.map((i) => i.ingredientId)}
      />
    </BrutalistCard>
  );
};

export default RecipeBOMStep;
