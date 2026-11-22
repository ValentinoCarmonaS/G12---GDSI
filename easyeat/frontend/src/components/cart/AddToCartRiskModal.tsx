import React, { useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { AllergenWarningBadge } from '../catalog/AllergenWarningBadge';
import { AccessibleModal } from '@/components/diner/AccessibleModal';
import { Button } from '@/components/ui/Button';

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
    <AccessibleModal
      isOpen={isOpen}
      onClose={handleCancel}
      title="Advertencia de Seguridad Alimentaria"
      subtitle="Este plato contiene ingredientes que infringen tus restricciones configuradas."
      size="sm"
      testId="add-to-cart-risk-modal"
      footer={
        <div className="flex items-center justify-end gap-3 w-full">
          <Button
            type="button"
            variant="secondary"
            onClick={handleCancel}
            data-testid="risk-modal-cancel-button"
            className="min-h-[44px]"
          >
            Cancelar
          </Button>
          <Button
            type="button"
            variant="danger"
            disabled={!hasAcknowledged}
            onClick={handleConfirm}
            data-testid="risk-modal-confirm-button"
            className="min-h-[44px]"
          >
            Confirmar y agregar
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div className="flex items-start gap-3 p-3 rounded-[var(--ee-radius-sm)] bg-[var(--ee-danger-bg)] border border-[var(--ee-danger-border)] text-[var(--ee-danger-fg)]">
          <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" aria-hidden="true" />
          <div className="text-xs leading-relaxed">
            <p className="font-semibold text-sm text-[var(--ee-danger-fg)] mb-1">
              Plato seleccionado: <span className="font-normal">{dishName}</span>
            </p>
            <p className="mb-2">Restricciones en conflicto:</p>
            <AllergenWarningBadge conflicts={conflictingRestrictions} />
          </div>
        </div>

        <div className="bg-[var(--ee-warning-bg)] p-3 rounded-[var(--ee-radius-sm)] border border-[var(--ee-warning-border)] text-xs text-[var(--ee-warning-fg)] leading-relaxed">
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
            className="mt-0.5 h-4 w-4 rounded border-[var(--ee-border-strong)] accent-[var(--ee-danger-solid)] focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
          />
          <span className="text-xs text-[var(--ee-text)] font-medium leading-relaxed">
            Entiendo el riesgo para mi salud y deseo agregarlo al carrito de todos modos.
          </span>
        </label>
      </div>
    </AccessibleModal>
  );
};

export default AddToCartRiskModal;
