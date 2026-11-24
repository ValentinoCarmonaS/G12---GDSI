import React, { useEffect, useRef, useState } from 'react';
import { AccessibleModal } from '@/components/diner';
import { Button } from '@/components/primitives';
import { COMMON_ALLERGENS, DIETARY_LIFESTYLES } from '@/constants/allergenTaxonomy';
import type { DishProposedChanges, DishProposedIngredient } from '@/types/dishReview';

export interface RejectionIngredientOption {
  ingredientId: string;
  ingredientName: string;
  unitOfMeasure?: string;
}

export interface RejectionDialogProps {
  isOpen: boolean;
  initialProposal?: DishProposedChanges | null;
  knownIngredients?: RejectionIngredientOption[];
  onConfirm: (comment: string, proposedChanges: DishProposedChanges | null) => void;
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
    <AccessibleModal
      isOpen={isOpen}
      onClose={onCancel}
      title="Rechazar y devolver a cocina"
      subtitle="Devolución de ficha"
      size="lg"
      footer={
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" size="md" onClick={onCancel}>
            Cancelar
          </Button>
          <Button
            type="button"
            variant="danger"
            size="md"
            disabled={!valid}
            onClick={() => onConfirm(comment, buildProposal())}
          >
            Rechazar
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div>
          <label className="block text-sm">
            <span className="font-medium text-ee-text">Comentario para el Chef</span>
            <textarea
              aria-label="Comentario para el Chef"
              className="mt-1 block w-full rounded-ee-sm border border-ee-border-strong bg-ee-surface-elevated px-3 py-2 text-sm text-ee-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
              rows={4}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
            />
          </label>

          <p
            className={`mt-1 font-ee-mono tabular-nums text-xs ${
              comment.length > MAX_LENGTH ? 'text-ee-danger-fg' : 'text-ee-text-secondary'
            }`}
          >
            {comment.length} / {MAX_LENGTH}
          </p>
        </div>

        <div className="space-y-3 border-t border-ee-border-subtle pt-3">
          <label className="flex items-center gap-2 text-sm font-medium text-ee-text">
            <input
              type="checkbox"
              checked={includeProposal}
              onChange={(e) => setIncludeProposal(e.target.checked)}
              className="h-4 w-4 rounded border-ee-border-strong text-ee-primary focus:ring-ee-focus-ring"
            />
            Incluir propuesta de cambios
          </label>

          {includeProposal && (
            <div className="space-y-4 rounded-ee-md border border-ee-border-subtle bg-ee-surface-subtle/50 p-4">
              <label className="block text-sm">
                <span className="font-medium text-ee-text">Título propuesto</span>
                <input
                  aria-label="Título propuesto"
                  className="mt-1 block w-full min-h-[44px] rounded-ee-sm border border-ee-border-strong bg-ee-surface-elevated px-3 py-2 text-sm text-ee-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                />
              </label>

              <label className="block text-sm">
                <span className="font-medium text-ee-text">Descripción propuesta</span>
                <textarea
                  aria-label="Descripción propuesta"
                  className="mt-1 block w-full rounded-ee-sm border border-ee-border-strong bg-ee-surface-elevated px-3 py-2 text-sm text-ee-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
                  rows={3}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </label>

              <fieldset>
                <legend className="text-sm font-medium text-ee-text">Alérgenos</legend>
                <div className="mt-1.5 flex flex-wrap gap-x-4 gap-y-2">
                  {COMMON_ALLERGENS.map((item) => (
                    <label
                      key={item.code}
                      className="flex items-center gap-1.5 text-sm text-ee-text"
                    >
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
                <legend className="text-sm font-medium text-ee-text">Estilos de vida</legend>
                <div className="mt-1.5 flex flex-wrap gap-x-4 gap-y-2">
                  {DIETARY_LIFESTYLES.map((item) => (
                    <label
                      key={item.code}
                      className="flex items-center gap-1.5 text-sm text-ee-text"
                    >
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
                <legend className="text-sm font-medium text-ee-text">
                  Ingredientes propuestos
                </legend>
                {ingredients.length === 0 ? (
                  <p className="mt-1 text-xs text-ee-text-secondary">Sin ingredientes propuestos.</p>
                ) : (
                  <ul className="mt-2 space-y-2">
                    {ingredients.map((item) => {
                      const label = ingredientLabel(item.ingredientId, knownIngredients);
                      return (
                        <li key={item.ingredientId} className="flex items-center gap-2">
                          <span className="flex-1 text-sm text-ee-text">{label}</span>
                          <input
                            type="number"
                            min="0"
                            step="any"
                            aria-label={`Cantidad de ${label}`}
                            className="w-24 min-h-[40px] rounded-ee-sm border border-ee-border-strong bg-ee-surface-elevated px-2 py-1 font-ee-mono tabular-nums text-sm text-ee-text"
                            value={quantities[item.ingredientId] ?? String(item.quantity)}
                            onChange={(e) =>
                              setQuantities((prev) => ({
                                ...prev,
                                [item.ingredientId]: e.target.value,
                              }))
                            }
                          />
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            aria-label={`Quitar ${label}`}
                            onClick={() => handleRemoveIngredient(item.ingredientId)}
                          >
                            Quitar
                          </Button>
                        </li>
                      );
                    })}
                  </ul>
                )}

                {addableIngredients.length > 0 && (
                  <div className="mt-2.5 flex items-center gap-2">
                    <select
                      aria-label="Ingrediente a agregar"
                      className="flex-1 min-h-[44px] rounded-ee-sm border border-ee-border-strong bg-ee-surface-elevated px-2.5 py-1.5 text-sm text-ee-text"
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
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      disabled={!newIngredientId}
                      onClick={handleAddIngredient}
                    >
                      Agregar
                    </Button>
                  </div>
                )}
              </fieldset>
            </div>
          )}
        </div>
      </div>
    </AccessibleModal>
  );
};

export default RejectionDialog;
