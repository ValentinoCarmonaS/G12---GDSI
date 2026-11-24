import React from 'react';
import { AlertTriangle } from 'lucide-react';
import { AccessibleModal } from '@/components/diner';
import { Button } from '@/components/primitives';
import type { AcknowledgementKind } from '@/types/dishReview';

export interface PublicationConfirmModalProps {
  isOpen: boolean;
  requiredAcknowledgements: AcknowledgementKind[];
  onConfirm: () => void;
  onCancel: () => void;
}

const ADVISORY_COPY: Record<AcknowledgementKind, string> = {
  UNPRICED_INGREDIENTS:
    'El plato tiene al menos un insumo sin costo de adquisición cargado: el Food Cost y el margen mostrados están incompletos.',
  NEGATIVE_MARGIN:
    'El margen comercial resultante es negativo: el precio de venta es menor al Food Cost.',
};

export const PublicationConfirmModal: React.FC<PublicationConfirmModalProps> = ({
  isOpen,
  requiredAcknowledgements,
  onConfirm,
  onCancel,
}) => (
  <AccessibleModal
    isOpen={isOpen}
    onClose={onCancel}
    title="Confirmación requerida antes de publicar"
    subtitle="Auditoría de publicación"
    size="md"
    footer={
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" size="md" onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="button" variant="primary" size="md" onClick={onConfirm}>
          Confirmar
        </Button>
      </div>
    }
  >
    <ul className="space-y-2.5 text-sm text-ee-warning-fg">
      {requiredAcknowledgements.map((kind) => (
        <li
          key={kind}
          className="flex items-start gap-2.5 rounded-ee-sm border border-ee-warning-border bg-ee-warning-bg p-3"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{ADVISORY_COPY[kind]}</span>
        </li>
      ))}
    </ul>
  </AccessibleModal>
);

export default PublicationConfirmModal;
