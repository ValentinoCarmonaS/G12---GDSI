import React, { useState } from 'react';
import { AllergenWarningBadge } from '../catalog/AllergenWarningBadge';

export interface AddToCartRiskModalProps {
  isOpen: boolean;
  dishName: string;
  conflictingRestrictions: string[];
  onConfirm: () => void;
  onCancel: () => void;
}

export const AddToCartRiskModal: React.FC<AddToCartRiskModalProps> = ({
  isOpen,
  dishName,
  conflictingRestrictions,
  onConfirm,
  onCancel,
}) => {
  const [hasAcknowledged, setHasAcknowledged] = useState(false);

  if (!isOpen) {
    return null;
  }

  const handleConfirm = () => {
    onConfirm();
    setHasAcknowledged(false);
  };

  const handleCancel = () => {
    setHasAcknowledged(false);
    onCancel();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="risk-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
      data-testid="add-to-cart-risk-modal"
    >
      <div className="bg-white rounded-xl max-w-md w-full shadow-2xl border border-red-200 overflow-hidden transform transition-all">
        {/* Header */}
        <div className="bg-red-50 p-4 border-b border-red-100 flex items-start gap-3">
          <div className="p-2 bg-red-100 text-red-600 rounded-lg flex-shrink-0">
            <svg
              className="h-6 w-6"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
          </div>
          <div>
            <h3 id="risk-modal-title" className="text-lg font-bold text-red-900">
              Advertencia de Seguridad Alimentaria
            </h3>
            <p className="text-xs text-red-700 mt-0.5">
              Este plato contiene ingredientes que infringen tus restricciones configuradas.
            </p>
          </div>
        </div>

        {/* Content */}
        <div className="p-5 space-y-4">
          <div>
            <p className="text-sm font-semibold text-gray-900 mb-1">
              Plato seleccionado: <span className="text-gray-700 font-normal">{dishName}</span>
            </p>
            <p className="text-xs text-gray-500 mb-2">Restricciones en conflicto:</p>
            <AllergenWarningBadge conflicts={conflictingRestrictions} />
          </div>

          <div className="bg-amber-50 p-3 rounded-lg border border-amber-200 text-xs text-amber-800 leading-relaxed">
            Consumir este plato puede conllevar riesgos para tu salud. La asunción de riesgo es
            responsabilidad exclusiva del comensal. El ítem permanecerá identificado con advertencias
            en el carrito.
          </div>

          <label className="flex items-start gap-2.5 cursor-pointer pt-1">
            <input
              type="checkbox"
              checked={hasAcknowledged}
              onChange={(e) => setHasAcknowledged(e.target.checked)}
              data-testid="risk-modal-acknowledge-checkbox"
              className="mt-0.5 h-4 w-4 rounded border-gray-300 text-red-600 focus:ring-red-500"
            />
            <span className="text-xs text-gray-700 font-medium">
              Entiendo el riesgo para mi salud y deseo agregarlo al carrito de todos modos.
            </span>
          </label>
        </div>

        {/* Actions */}
        <div className="bg-gray-50 px-5 py-3.5 border-t border-gray-100 flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={handleCancel}
            data-testid="risk-modal-cancel-button"
            className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-400"
          >
            Cancelar
          </button>
          <button
            type="button"
            disabled={!hasAcknowledged}
            onClick={handleConfirm}
            data-testid="risk-modal-confirm-button"
            className={`px-4 py-2 text-sm font-medium text-white rounded-lg focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 ${
              hasAcknowledged
                ? 'bg-red-600 hover:bg-red-700 cursor-pointer shadow-sm'
                : 'bg-red-300 cursor-not-allowed opacity-70'
            }`}
          >
            Confirmar y agregar
          </button>
        </div>
      </div>
    </div>
  );
};

export default AddToCartRiskModal;
