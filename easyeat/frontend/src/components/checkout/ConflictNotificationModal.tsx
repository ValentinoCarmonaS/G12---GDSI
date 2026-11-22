import React from 'react';
import { AlertCircle, Clock, RefreshCw } from 'lucide-react';
import { AccessibleModal } from '../diner/AccessibleModal';
import { Button } from '../primitives/Button';

export interface ConflictNotificationModalProps {
  isOpen: boolean;
  errorCode: 'SLOT_SOLD_OUT' | 'ACTIVE_RESERVATION_EXISTS' | string;
  errorMessage?: string;
  onClose: () => void;
  onRefreshSlots?: () => void;
  onViewExistingOrder?: () => void;
}

export const ConflictNotificationModal: React.FC<ConflictNotificationModalProps> = ({
  isOpen,
  errorCode,
  errorMessage,
  onClose,
  onRefreshSlots,
  onViewExistingOrder,
}) => {
  const isSoldOut = errorCode === 'SLOT_SOLD_OUT';
  const isActiveReservation = errorCode === 'ACTIVE_RESERVATION_EXISTS';

  const title = isSoldOut
    ? 'Franja horaria agotada'
    : isActiveReservation
    ? 'Reserva activa en curso'
    : 'Aviso de reserva';

  const defaultDetail = isSoldOut
    ? 'La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar.'
    : isActiveReservation
    ? 'Ya poseés una orden pendiente de confirmación. Por favor, completá o cancelá tu reserva actual para continuar.'
    : 'Ocurrió un conflicto al procesar tu reserva.';

  const detail = errorMessage || defaultDetail;

  return (
    <AccessibleModal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      size="sm"
      testId="conflict-notification-modal"
      closeButtonLabel="Cerrar modal"
      footer={
        <div className="flex flex-col sm:flex-row gap-3">
          {isSoldOut && (
            <Button
              variant="primary"
              size="md"
              fullWidth
              leftIcon={<RefreshCw className="w-4 h-4" />}
              onClick={() => {
                if (onRefreshSlots) {
                  onRefreshSlots();
                }
                onClose();
              }}
            >
              Elegir otra franja
            </Button>
          )}

          {isActiveReservation && (
            <Button
              variant="primary"
              size="md"
              fullWidth
              onClick={() => {
                if (onViewExistingOrder) {
                  onViewExistingOrder();
                }
                onClose();
              }}
            >
              Ver mi reserva
            </Button>
          )}

          <Button variant="outline" size="md" fullWidth onClick={onClose}>
            Cerrar
          </Button>
        </div>
      }
    >
      <div className="flex items-start gap-4">
        <div className="p-3 rounded-ee-md bg-ee-warning-bg text-ee-warning-fg border border-ee-warning-border shrink-0">
          {isSoldOut ? (
            <Clock className="w-6 h-6" aria-hidden="true" />
          ) : (
            <AlertCircle className="w-6 h-6" aria-hidden="true" />
          )}
        </div>
        <p className="text-sm text-ee-text-secondary leading-relaxed">{detail}</p>
      </div>
    </AccessibleModal>
  );
};
