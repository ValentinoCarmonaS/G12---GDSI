import React from 'react';
import type { AcknowledgementKind } from '@/types/dishReview';

export interface PublicationConfirmModalProps {
  isOpen: boolean;
  /** One entry per advisory FR-017 requires acknowledged before this approval can proceed. */
  requiredAcknowledgements: AcknowledgementKind[];
  /** Called only once the Administrator gives the explicit second confirmation (FR-031). */
  onConfirm: () => void;
  /** Called on abandonment. Confirms nothing: the dish's status, price and recipe stay as they
   * were, and no audit entry is implied (FR-031). */
  onCancel: () => void;
}

const ADVISORY_COPY: Record<AcknowledgementKind, string> = {
  UNPRICED_INGREDIENTS:
    'El plato tiene al menos un insumo sin costo de adquisición cargado: el Food Cost y el margen mostrados están incompletos.',
  NEGATIVE_MARGIN:
    'El margen comercial resultante es negativo: el precio de venta es menor al Food Cost.',
};

/**
 * The second, explicit confirmation FR-017 requires before approving a dish that carries an
 * unpriced ingredient or a negative margin (FR-031). Subordinate to the publication gate by
 * construction: this modal is never shown in place of a gate failure, only alongside a gate that
 * has already passed — {@link com.easyeat.dish.service.DishGovernanceService#approve} enforces
 * that ordering server-side, and this component carries no logic that could offer it as a
 * workaround.
 */
export const PublicationConfirmModal: React.FC<PublicationConfirmModalProps> = ({
  isOpen,
  requiredAcknowledgements,
  onConfirm,
  onCancel,
}) => {
  if (!isOpen) {
    return null;
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="publication-confirm-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50"
    >
      <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-lg space-y-4">
        <h2 id="publication-confirm-title" className="text-base font-semibold text-gray-900">
          Confirmación requerida antes de publicar
        </h2>

        <ul className="space-y-2 text-sm text-amber-800">
          {requiredAcknowledgements.map((kind) => (
            <li key={kind} className="rounded border border-amber-300 bg-amber-50 p-2">
              {ADVISORY_COPY[kind]}
            </li>
          ))}
        </ul>

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
            className="rounded bg-amber-700 px-3 py-1.5 text-sm text-white"
            onClick={onConfirm}
          >
            Confirmar
          </button>
        </div>
      </div>
    </div>
  );
};

export default PublicationConfirmModal;
