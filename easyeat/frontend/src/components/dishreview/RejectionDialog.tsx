import React, { useEffect, useRef, useState } from 'react';
import { COMMON_ALLERGENS, DIETARY_LIFESTYLES } from '@/constants/allergenTaxonomy';
import type { DishProposedChanges, DishProposedIngredient } from '@/types/dishReview';

/** A known ingredient the dialog can add to the proposal, with its human label. */
export interface RejectionIngredientOption {
  ingredientId: string;
  ingredientName: string;
  unitOfMeasure?: string;
}

export interface RejectionDialogProps {
  isOpen: boolean;
  /**
   * The dish as it stands today, preloaded into the editable proposal form (FR-042). The
   * Administrator can change any field before rejecting.
   */
  initialProposal?: DishProposedChanges | null;
  /** Ingredients already known for this dish, offered for the proposal's ingredient lines. */
  knownIngredients?: RejectionIngredientOption[];
  /**
   * Called only once the comment satisfies FR-021 (10-1000 characters after trimming, never
   * blank). Receives the comment exactly as typed, never pre-trimmed, plus the proposed edition
   * or `null` when the Administrator chose not to include one.
   */
  onConfirm: (comment: string, proposedChanges: DishProposedChanges | null) => void;
  /** Called on abandonment. The dish's status, price and recipe stay as they were, and no audit
   * entry is implied (FR-031). */
  onCancel: () => void;
}

const MIN_LENGTH = 10;
const MAX_LENGTH = 1000;

function isValid(comment: string): boolean {
  const trimmed = comment.trim();
  return trimmed.length >= MIN_LENGTH && comment.length <= MAX_LENGTH;
}

function ingredientLabel(
  ingredientId: string,
  knownIngredients: RejectionIngredientOption[]
): string {
  return (
    knownIngredients.find((item) => item.ingredientId === ingredientId)?.ingredientName ??
    `Ingrediente ${ingredientId.slice(0, 8)}`
  );
}

function toggled(codes: string[], code: string): string[] {
  return codes.includes(code) ? codes.filter((c) => c !== code) : [...codes, code];
}

/**
 * The mandatory rejection comment of FR-021 plus the Administrator's proposed edition of the dish
 * (FR-042), preloaded from the dish's current culinary file and fully editable.
 *
 * The proposal is optional: the Administrator opts in with a checkbox, and a blank/turned-off
 * proposal travels as `null`. Nothing in this form — or in `DishProposedChanges` — carries a cost,
 * Food Cost, margin or retail price, which is what keeps the Chef-facing projection blind.
 */
export const RejectionDialog: React.FC<RejectionDialogProps> = ({
  isOpen,
  initialProposal,
  knownIngredients = [],
  onConfirm,
  onCancel,
}) => {
  const [comment, setComment] = useState('');
  const [includeProposal, setIncludeProposal] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [allergenCodes, setAllergenCodes] = useState<string[]>([]);
  const [lifestyleCodes, setLifestyleCodes] = useState<string[]>([]);
  const [ingredients, setIngredients] = useState<DishProposedIngredient[]>([]);
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [newIngredientId, setNewIngredientId] = useState('');

  const wasOpen = useRef(false);

  // Reset the form each time the dialog opens, so a previous cycle's draft never leaks into the
  // next rejection. The ref keeps in-progress typing from being reset by parent re-renders.
  useEffect(() => {
    if (isOpen && !wasOpen.current) {
      setComment('');
      setIncludeProposal(false);
      setTitle(initialProposal?.title ?? '');
      setDescription(initialProposal?.description ?? '');
      setAllergenCodes(initialProposal?.allergenCodes ?? []);
      setLifestyleCodes(initialProposal?.dietaryLifestyleCodes ?? []);
      const proposed = initialProposal?.ingredients ?? [];
      setIngredients(proposed);
      setQuantities(
        Object.fromEntries(proposed.map((item) => [item.ingredientId, String(item.quantity)]))
      );
      setNewIngredientId('');
    }
    wasOpen.current = isOpen;
  }, [isOpen, initialProposal]);

  if (!isOpen) {
    return null;
  }

  const valid = isValid(comment);
  const addableIngredients = knownIngredients.filter(
    (option) => !ingredients.some((item) => item.ingredientId === option.ingredientId)
  );

  function handleAddIngredient() {
    if (!newIngredientId) return;
    setIngredients((prev) => [...prev, { ingredientId: newIngredientId, quantity: 1 }]);
    setQuantities((prev) => ({ ...prev, [newIngredientId]: '1' }));
    setNewIngredientId('');
  }

  function handleRemoveIngredient(ingredientId: string) {
    setIngredients((prev) => prev.filter((item) => item.ingredientId !== ingredientId));
    setQuantities((prev) => {
      const next = { ...prev };
      delete next[ingredientId];
      return next;
    });
  }

  function buildProposal(): DishProposedChanges | null {
    if (!includeProposal) return null;

    const proposedIngredients = ingredients
      .map((item) => ({
        ingredientId: item.ingredientId,
        quantity: Number(quantities[item.ingredientId] ?? item.quantity),
      }))
      .filter((item) => Number.isFinite(item.quantity) && item.quantity > 0);

    const proposal: DishProposedChanges = {
      title: title.trim() || null,
      description: description.trim() || null,
      allergenCodes,
      dietaryLifestyleCodes: lifestyleCodes,
      ingredients: proposedIngredients,
    };

    const blank =
      !proposal.title &&
      !proposal.description &&
      allergenCodes.length === 0 &&
      lifestyleCodes.length === 0 &&
      proposedIngredients.length === 0;

    return blank ? null : proposal;
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="rejection-dialog-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50"
    >
      <div className="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-lg bg-white p-5 shadow-lg space-y-3">
        <h2 id="rejection-dialog-title" className="text-base font-semibold text-gray-900">
          Rechazar y devolver a cocina
        </h2>

        <label className="block text-sm">
          <span className="text-gray-700">Comentario para el Chef</span>
          <textarea
            aria-label="Comentario para el Chef"
            className="mt-1 block w-full rounded border border-gray-300 px-2 py-1.5 text-sm"
            rows={4}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
        </label>

        <p className={`text-xs ${comment.length > MAX_LENGTH ? 'text-red-600' : 'text-gray-500'}`}>
          {comment.length} / {MAX_LENGTH}
        </p>

        <div className="space-y-3 border-t border-gray-200 pt-3">
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={includeProposal}
              onChange={(e) => setIncludeProposal(e.target.checked)}
            />
            Incluir propuesta de cambios
          </label>

          {includeProposal && (
            <div className="space-y-4">
              <label className="block text-sm">
                <span className="text-gray-700">Título propuesto</span>
                <input
                  aria-label="Título propuesto"
                  className="mt-1 block w-full rounded border border-gray-300 px-2 py-1.5 text-sm"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                />
              </label>

              <label className="block text-sm">
                <span className="text-gray-700">Descripción propuesta</span>
                <textarea
                  aria-label="Descripción propuesta"
                  className="mt-1 block w-full rounded border border-gray-300 px-2 py-1.5 text-sm"
                  rows={3}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </label>

              <fieldset>
                <legend className="text-sm text-gray-700">Alérgenos</legend>
                <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1">
                  {COMMON_ALLERGENS.map((item) => (
                    <label key={item.code} className="flex items-center gap-1.5 text-sm text-gray-700">
                      <input
                        type="checkbox"
                        aria-label={item.name}
                        checked={allergenCodes.includes(item.code)}
                        onChange={() => setAllergenCodes((prev) => toggled(prev, item.code))}
                      />
                      {item.name}
                    </label>
                  ))}
                </div>
              </fieldset>

              <fieldset>
                <legend className="text-sm text-gray-700">Estilos de vida</legend>
                <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1">
                  {DIETARY_LIFESTYLES.map((item) => (
                    <label key={item.code} className="flex items-center gap-1.5 text-sm text-gray-700">
                      <input
                        type="checkbox"
                        aria-label={item.name}
                        checked={lifestyleCodes.includes(item.code)}
                        onChange={() => setLifestyleCodes((prev) => toggled(prev, item.code))}
                      />
                      {item.name}
                    </label>
                  ))}
                </div>
              </fieldset>

              <fieldset>
                <legend className="text-sm text-gray-700">Ingredientes propuestos</legend>
                {ingredients.length === 0 ? (
                  <p className="mt-1 text-xs text-gray-500">Sin ingredientes propuestos.</p>
                ) : (
                  <ul className="mt-1 space-y-2">
                    {ingredients.map((item) => {
                      const label = ingredientLabel(item.ingredientId, knownIngredients);
                      return (
                        <li key={item.ingredientId} className="flex items-center gap-2">
                          <span className="flex-1 text-sm text-gray-800">{label}</span>
                          <input
                            type="number"
                            min="0"
                            step="any"
                            aria-label={`Cantidad de ${label}`}
                            className="w-24 rounded border border-gray-300 px-2 py-1 text-sm"
                            value={quantities[item.ingredientId] ?? String(item.quantity)}
                            onChange={(e) =>
                              setQuantities((prev) => ({
                                ...prev,
                                [item.ingredientId]: e.target.value,
                              }))
                            }
                          />
                          <button
                            type="button"
                            aria-label={`Quitar ${label}`}
                            className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-700"
                            onClick={() => handleRemoveIngredient(item.ingredientId)}
                          >
                            Quitar
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                )}

                {addableIngredients.length > 0 && (
                  <div className="mt-2 flex items-center gap-2">
                    <select
                      aria-label="Ingrediente a agregar"
                      className="flex-1 rounded border border-gray-300 px-2 py-1 text-sm"
                      value={newIngredientId}
                      onChange={(e) => setNewIngredientId(e.target.value)}
                    >
                      <option value="">Seleccionar ingrediente…</option>
                      {addableIngredients.map((option) => (
                        <option key={option.ingredientId} value={option.ingredientId}>
                          {option.ingredientName}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      className="rounded border border-gray-300 px-2 py-1 text-sm text-gray-700 disabled:text-gray-400"
                      disabled={!newIngredientId}
                      onClick={handleAddIngredient}
                    >
                      Agregar
                    </button>
                  </div>
                )}
              </fieldset>
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2">
          <button
            type="button"
            className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700"
            onClick={onCancel}
          >
            Cancelar
          </button>
          <button
            type="button"
            className="rounded bg-red-700 px-3 py-1.5 text-sm text-white disabled:bg-gray-300"
            disabled={!valid}
            onClick={() => onConfirm(comment, buildProposal())}
          >
            Rechazar
          </button>
        </div>
      </div>
    </div>
  );
};

export default RejectionDialog;
