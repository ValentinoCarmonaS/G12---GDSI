import React from 'react';
import { User, AlertTriangle } from 'lucide-react';
import { AccessibleModal } from '../diner/AccessibleModal';
import { BentoCard } from '../diner/BentoCard';
import { Button } from '../primitives/Button';

export interface PhotoDeleteConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

export const PhotoDeleteConfirmModal: React.FC<PhotoDeleteConfirmModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
}) => {
  return (
    <AccessibleModal
      isOpen={isOpen}
      onClose={onClose}
      title="¿Desea eliminar su foto de perfil?"
      size="sm"
      footer={
        <div className="flex w-full items-center justify-end gap-3">
          <Button type="button" variant="secondary" onClick={onClose} className="min-h-[44px]">
            Cancelar
          </Button>
          <Button
            type="button"
            variant="danger"
            onClick={() => {
              onConfirm();
              onClose();
            }}
            className="min-h-[44px]"
          >
            Confirmar eliminación
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div className="flex items-start gap-3">
          <div className="shrink-0 rounded-ee-sm border border-ee-warning-border bg-ee-warning-bg p-2.5 text-ee-warning-text">
            <AlertTriangle className="h-5 w-5" aria-hidden="true" />
          </div>
          <p className="text-sm text-ee-text-secondary">
            Se restablecerá el avatar institucional por defecto. La eliminación se consolidará de manera efectiva al presionar &quot;Guardar cambios&quot; en el formulario de datos.
          </p>
        </div>

        <BentoCard variant="subtle" padding="sm" className="flex flex-col items-center justify-center">
          <span className="mb-2 text-xs font-medium text-ee-text-muted">Vista previa provisional</span>
          <div className="flex h-16 w-16 items-center justify-center rounded-full border-2 border-dashed border-ee-border-strong bg-ee-surface-elevated text-ee-text-muted">
            <User className="h-8 w-8" aria-hidden="true" />
          </div>
        </BentoCard>
      </div>
    </AccessibleModal>
  );
};

export default PhotoDeleteConfirmModal;
