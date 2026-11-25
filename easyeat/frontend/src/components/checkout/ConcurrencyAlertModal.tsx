import React from 'react';
import { AlertTriangle } from 'lucide-react';
import { AccessibleModal } from '../diner/AccessibleModal';
import { Button } from '../primitives/Button';
import { StatusBadge } from '../primitives/StatusBadge';

export interface UnavailableDishDetail {
  dishId: string;
  dishName: string;
  limitingIngredient?: string;
}

interface ConcurrencyAlertModalProps {
  isOpen: boolean;
  type: 'INSUFFICIENT_STOCK' | 'SLOT_CAPACITY_EXCEEDED';
  message?: string;
  unavailableDishes?: UnavailableDishDetail[];
  onAction: () => void;
  onClose: () => void;
}

export const ConcurrencyAlertModal: React.FC<ConcurrencyAlertModalProps> = ({
  isOpen,
  type,
  message,
  unavailableDishes = [],
  onAction,
  onClose,
}) => {
  const isStock = type === 'INSUFFICIENT_STOCK';
  const title = isStock ? 'Insumos agotados por concurrencia' : 'Franja horaria saturada';

  return (
    <AccessibleModal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      size="md"
      closeButtonLabel="Cerrar modal"
      footer={
        <div className="flex flex-col-reverse sm:flex-row justify-end gap-3">
          <Button variant="outline" size="md" onClick={onClose}>
            Cerrar
          </Button>
          <Button variant="primary" size="md" onClick={onAction}>
            {isStock ? 'Modificar carrito' : 'Elegir otro horario'}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div className="flex items-start gap-3 p-3.5 rounded-ee-md bg-ee-warning-bg border border-ee-warning-border text-ee-warning-fg">
          <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" aria-hidden="true" />
          <div className="space-y-1">
            <p className="text-sm font-semibold">
              Tus productos se mantuvieron guardados en el carrito
            </p>
            <p className="text-sm leading-relaxed">
              {message ||
                (isStock
                  ? 'Los insumos de uno o más platos se agotaron por alta demanda concurrente antes de consolidar la reserva.'
                  : 'La franja horaria seleccionada ha alcanzado su cupo máximo luego de expirar tu reserva de 15 minutos. Por favor, elegí un nuevo horario de retiro.')}
            </p>
          </div>
        </div>

        {isStock && unavailableDishes.length > 0 && (
          <div className="bg-ee-surface-subtle border border-ee-border-subtle rounded-ee-md p-4 space-y-2">
            <span className="font-ee-mono text-xs font-semibold text-ee-text-secondary uppercase tracking-wider">
              Platos afectados
            </span>
            <ul className="divide-y divide-ee-border-subtle">
              {unavailableDishes.map((dish) => (
                <li key={dish.dishId} className="py-2 flex justify-between items-center gap-3 text-sm">
                  <span className="font-semibold text-ee-text">{dish.dishName}</span>
                  {dish.limitingIngredient && (
                    <StatusBadge variant="danger">Falta: {dish.limitingIngredient}</StatusBadge>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </AccessibleModal>
  );
};
