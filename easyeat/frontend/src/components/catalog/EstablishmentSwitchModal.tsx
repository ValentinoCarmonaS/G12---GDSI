import React from 'react';
import { AlertTriangle } from 'lucide-react';
import { EstablishmentSummary } from '@/types/dinerCatalog';
import { AccessibleModal } from '@/components/diner/AccessibleModal';
import { Button } from '@/components/ui/Button';

export interface EstablishmentSwitchModalProps {
  isOpen: boolean;
  targetEstablishment?: EstablishmentSummary | null;
  currentEstablishmentName?: string;
  newEstablishmentName?: string;
  cartItemsCount?: number;
  onConfirm: () => void;
  onCancel: () => void;
}

export const EstablishmentSwitchModal: React.FC<EstablishmentSwitchModalProps> = ({
  isOpen,
  targetEstablishment,
  newEstablishmentName,
  onConfirm,
  onCancel,
}) => {
  const targetName = targetEstablishment?.name || newEstablishmentName;

  if (!isOpen || !targetName) {
    return null;
  }

  return (
    <AccessibleModal
      isOpen={isOpen}
      onClose={onCancel}
      title="¿Cambiar de restaurante?"
      size="sm"
      footer={
        <div className="flex items-center justify-end gap-3 w-full">
          <Button
            type="button"
            variant="secondary"
            onClick={onCancel}
            className="min-h-[44px]"
          >
            Cancelar
          </Button>
          <Button
            type="button"
            variant="primary"
            onClick={onConfirm}
            className="min-h-[44px]"
          >
            Cambiar de local
          </Button>
        </div>
      }
    >
      <div className="flex items-start gap-3">
        <div className="p-2.5 bg-[var(--ee-warning-bg)] border border-[var(--ee-warning-border)] text-[var(--ee-warning-fg)] rounded-[var(--ee-radius-sm)] shrink-0">
          <AlertTriangle className="w-5 h-5" aria-hidden="true" />
        </div>
        <p className="text-sm text-[var(--ee-text-secondary)] leading-relaxed">
          Tenés productos de otro local en tu carrito. Si cambiás a{' '}
          <span className="font-semibold text-[var(--ee-text)]">{targetName}</span>, se
          vaciará tu carrito para evitar mezclar pedidos de distintos restaurantes.
        </p>
      </div>
    </AccessibleModal>
  );
};
