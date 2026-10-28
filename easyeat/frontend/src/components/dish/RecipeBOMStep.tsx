import React, { useState, useEffect } from 'react';
import { IngredientSelectorModal } from './IngredientSelectorModal';
import type { RecipeIngredientItem } from '@/types/dish';
import type { ChefIngredientItemResponse } from '@/types/inventory';

export interface RecipeBOMStepProps {
  dishId?: string;
  initialRecipe?: RecipeIngredientItem[];
  onSaveRecipe?: (recipe: RecipeIngredientItem[]) => void | Promise<void>;
  onRemoveIngredient?: (ingredientId: string) => void | Promise<void>;
  isReadOnly?: boolean;
  isSaving?: boolean;
}

export const RecipeBOMStep: React.FC<RecipeBOMStepProps> = ({
  initialRecipe = [],
  onSaveRecipe,
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

  const handleSelectIngredient = (selected: ChefIngredientItemResponse) => {
    const exists = ingredients.some((i) => i.ingredientId === selected.id);

    if (exists) {
      setDuplicateWarning(
        `El ingrediente ya se encuentra en la receta ("${selected.name}"). Podés ajustar su cantidad directamente en la tabla.`
      );
      return;
    }

    setDuplicateWarning(null);
    const defaultQty = 1;
    const newItem: RecipeIngredientItem = {
      ingredientId: selected.id,
      ingredientName: selected.name,
      quantity: defaultQty,
      unitOfMeasure: selected.unitOfMeasure,
    };

    setIngredients((prev) => [...prev, newItem]);
    setRawQuantities((prev) => ({
      ...prev,
      [selected.id]: defaultQty.toString(),
    }));
  };

  const handleQuantityChange = (ingredientId: string, value: string) => {
    setRawQuantities((prev) => ({ ...prev, [ingredientId]: value }));

    // Clear validation error on change
    if (validationErrors[ingredientId]) {
      setValidationErrors((prev) => {
        const next = { ...prev };
        delete next[ingredientId];
        return next;
      });
    }

    const numValue = parseFloat(value);
    setIngredients((prev) =>
      prev.map((item) =>
        item.ingredientId === ingredientId
          ? { ...item, quantity: isNaN(numValue) ? 0 : numValue }
          : item
      )
    );
  };

  const handleRemove = (ingredientId: string) => {
    setIngredients((prev) => prev.filter((i) => i.ingredientId !== ingredientId));
    setRawQuantities((prev) => {
      const next = { ...prev };
      delete next[ingredientId];
      return next;
    });
    setValidationErrors((prev) => {
      const next = { ...prev };
      delete next[ingredientId];
      return next;
    });
    setDuplicateWarning(null);

    if (onRemoveIngredient) {
      onRemoveIngredient(ingredientId);
    }
  };

  const validate = (): boolean => {
    const errors: Record<string, string> = {};

    for (const item of ingredients) {
      const rawVal = rawQuantities[item.ingredientId] ?? item.quantity.toString();
      const num = parseFloat(rawVal);

      if (isNaN(num) || num <= 0) {
        errors[item.ingredientId] = 'La cantidad debe ser mayor a 0.';
        continue;
      }

      if (item.unitOfMeasure === 'u') {
        if (!Number.isInteger(num)) {
          errors[item.ingredientId] =
            'Las unidades discretas (\'u\') solo aceptan números enteros.';
        }
      }
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSave = () => {
    if (!validate()) {
      return;
    }

    if (onSaveRecipe) {
      // Sync ingredients with parsed quantities
      const finalItems = ingredients.map((item) => ({
        ...item,
        quantity: parseFloat(rawQuantities[item.ingredientId] ?? item.quantity.toString()),
      }));
      onSaveRecipe(finalItems);
    }
  };

  return (
    <div className="space-y-6">
      {/* Step Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-gray-800">
        <div>
          <h2 className="text-xl font-bold text-white tracking-wide">
            Receta y Escandallo (BOM)
          </h2>
          <p className="text-sm text-gray-400 mt-1">
            Componé la lista de ingredientes activos y definí las cantidades exactas requeridas para preparar este plato.
          </p>
        </div>

        {!isReadOnly && (
          <button
            type="button"
            onClick={handleOpenModal}
            className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-amber-500 hover:bg-amber-600 text-black font-semibold text-sm rounded-lg transition-colors shadow-sm"
          >
            + Agregar ingrediente
          </button>
        )}
      </div>

      {/* Duplicate Warning Banner */}
      {duplicateWarning && (
        <div
          role="alert"
          className="p-4 bg-amber-950/70 border border-amber-800 text-amber-200 rounded-lg flex items-center justify-between gap-3 text-sm animate-fadeIn"
        >
          <div className="flex items-center gap-2">
            <span className="text-amber-400 text-base">⚠️</span>
            <span>{duplicateWarning}</span>
          </div>
          <button
            type="button"
            onClick={() => setDuplicateWarning(null)}
            className="text-amber-400 hover:text-white text-xs px-2 py-1 rounded bg-amber-900/40 hover:bg-amber-900/80 transition-colors"
          >
            Entendido
          </button>
        </div>
      )}

      {/* Ingredients Table */}
      {ingredients.length === 0 ? (
        <div className="text-center py-12 px-4 bg-gray-900/50 border border-dashed border-gray-800 rounded-xl">
          <p className="text-gray-400 text-sm font-medium">No hay ingredientes agregados</p>
          <p className="text-gray-500 text-xs mt-1">
            Hacé clic en &quot;Agregar ingrediente&quot; para seleccionar del catálogo activo del establecimiento.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto bg-gray-900/60 border border-gray-800 rounded-xl">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-800 text-gray-400 text-xs uppercase tracking-wider bg-gray-900/80">
                <th className="py-3 px-4">Ingrediente</th>
                <th className="py-3 px-4 w-40">Cantidad</th>
                <th className="py-3 px-4 w-28 text-center">Unidad</th>
                {!isReadOnly && <th className="py-3 px-4 w-24 text-center">Acciones</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-800">
              {ingredients.map((item) => {
                const currentVal = rawQuantities[item.ingredientId] ?? item.quantity.toString();
                const itemError = validationErrors[item.ingredientId];

                return (
                  <tr key={item.ingredientId} className="hover:bg-gray-800/40 transition-colors">
                    <td className="py-3 px-4">
                      <div className="font-medium text-white">{item.ingredientName}</div>
                      {itemError && (
                        <p className="text-xs text-red-400 mt-1 font-normal">{itemError}</p>
                      )}
                    </td>
                    <td className="py-3 px-4">
                      {isReadOnly ? (
                        <span className="font-mono text-gray-200">{currentVal}</span>
                      ) : (
                        <div>
                          <input
                            type="text"
                            inputMode="decimal"
                            value={currentVal}
                            onChange={(e) => handleQuantityChange(item.ingredientId, e.target.value)}
                            className={`w-full bg-gray-800 border rounded-lg px-3 py-1.5 text-sm text-white font-mono focus:outline-none focus:ring-2 ${
                              itemError
                                ? 'border-red-500 focus:ring-red-500'
                                : 'border-gray-700 focus:ring-amber-500'
                            }`}
                          />
                        </div>
                      )}
                    </td>
                    <td className="py-3 px-4 text-center">
                      <span className="inline-block px-2 py-0.5 text-xs font-mono bg-gray-800 text-gray-300 rounded border border-gray-700">
                        {item.unitOfMeasure}
                      </span>
                    </td>
                    {!isReadOnly && (
                      <td className="py-3 px-4 text-center">
                        <button
                          type="button"
                          onClick={() => handleRemove(item.ingredientId)}
                          className="px-2.5 py-1 text-xs text-red-400 hover:text-red-300 hover:bg-red-950/40 rounded transition-colors"
                          aria-label={`Eliminar ${item.ingredientName}`}
                        >
                          Eliminar
                        </button>
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Save Action */}
      {!isReadOnly && (
        <div className="flex justify-end pt-4 border-t border-gray-800">
          <button
            type="button"
            disabled={isSaving}
            onClick={handleSave}
            className="px-6 py-2.5 bg-amber-500 hover:bg-amber-600 disabled:opacity-50 disabled:cursor-not-allowed text-black font-semibold text-sm rounded-lg transition-colors shadow-sm"
          >
            {isSaving ? 'Guardando receta...' : 'Guardar receta'}
          </button>
        </div>
      )}

      {/* Ingredient Selector Modal */}
      <IngredientSelectorModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSelectIngredient={handleSelectIngredient}
        selectedIngredientIds={ingredients.map((i) => i.ingredientId)}
      />
    </div>
  );
};
