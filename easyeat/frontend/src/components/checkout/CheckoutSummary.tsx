import React from 'react';
import { AlertTriangle, ShoppingBag } from 'lucide-react';
import { CartItem } from '../../types/cart';
import { FulfillmentMode, TimeSlotAvailability } from '../../types/order';
import { BentoCard } from '../diner/BentoCard';

interface CheckoutSummaryProps {
  items: CartItem[];
  fulfillmentMode: FulfillmentMode | null;
  selectedDate?: string | null;
  selectedSlot?: TimeSlotAvailability | null;
  totalAmount?: number;
  action?: React.ReactNode;
}

export const CheckoutSummary: React.FC<CheckoutSummaryProps> = ({
  items,
  fulfillmentMode,
  selectedDate,
  selectedSlot,
  totalAmount,
  action,
}) => {
  const calculatedTotal =
    totalAmount !== undefined
      ? totalAmount
      : items.reduce((sum, item) => sum + item.price * item.quantity, 0);

  const formatPrice = (value: number) => {
    return `$${value.toFixed(2)}`;
  };

  const formatTime = (timeStr?: string | null) => {
    if (!timeStr) return '';
    return timeStr.slice(0, 5); // "HH:mm"
  };

  const formatDate = (dateStr?: string | null) => {
    if (!dateStr) return '';
    // Format YYYY-MM-DD to DD/MM/YYYY
    const parts = dateStr.split('-');
    if (parts.length === 3) {
      return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return dateStr;
  };

  const isHighDemand =
    fulfillmentMode === 'SCHEDULED' &&
    selectedSlot !== null &&
    selectedSlot !== undefined &&
    (selectedSlot.status === 'HIGH_DEMAND' ||
      (selectedSlot.occupancyPercentage >= 80 && selectedSlot.occupancyPercentage < 100));

  return (
    <BentoCard variant="elevated" padding="md" radius="lg" as="aside" className="space-y-6">
      <div className="flex items-center gap-2 border-b border-ee-border-subtle pb-4">
        <ShoppingBag className="w-5 h-5 text-ee-primary-border" aria-hidden="true" />
        <h2 className="font-ee-display text-lg font-bold text-ee-text">Resumen del pedido</h2>
      </div>

      {/* Items list */}
      <div className="space-y-3">
        {items.length === 0 ? (
          <p className="text-sm text-ee-text-secondary italic">No hay ítems en el carrito.</p>
        ) : (
          <ul className="divide-y divide-ee-border-subtle">
            {items.map((item) => (
              <li
                key={item.dishId}
                className="py-2.5 flex items-center justify-between gap-3 text-sm"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <span className="font-ee-mono tabular-nums font-semibold text-ee-text shrink-0">
                    {item.quantity}x
                  </span>
                  <span className="text-ee-text truncate">{item.name}</span>
                </div>
                <span className="font-ee-mono tabular-nums font-semibold text-ee-text shrink-0">
                  {formatPrice(item.price * item.quantity)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Fulfillment details */}
      <div className="bg-ee-surface-subtle rounded-ee-md p-4 space-y-2 border border-ee-border-subtle text-sm">
        <div className="flex items-center justify-between gap-2">
          <span className="text-ee-text-secondary font-medium">Modalidad:</span>
          <span className="font-semibold text-ee-text text-right">
            {fulfillmentMode === 'IMMEDIATE'
              ? 'Lo antes posible (Inmediato)'
              : fulfillmentMode === 'SCHEDULED'
              ? 'Para más tarde (Turno programado)'
              : 'No seleccionada'}
          </span>
        </div>

        {fulfillmentMode === 'SCHEDULED' && (
          <>
            <div className="flex items-center justify-between gap-2">
              <span className="text-ee-text-secondary font-medium">Fecha de retiro:</span>
              <span className="font-ee-mono tabular-nums font-semibold text-ee-text">
                {selectedDate ? formatDate(selectedDate) : 'Pendiente de selección'}
              </span>
            </div>

            <div className="flex items-center justify-between gap-2">
              <span className="text-ee-text-secondary font-medium">Horario de retiro:</span>
              <span className="font-ee-mono tabular-nums font-semibold text-ee-text">
                {selectedSlot
                  ? `${formatTime(selectedSlot.startTime)} - ${formatTime(selectedSlot.endTime)} hs`
                  : 'Pendiente de selección'}
              </span>
            </div>
          </>
        )}
      </div>

      {/* Non-blocking preventive alert for High Demand slots */}
      {isHighDemand && (
        <div
          role="alert"
          className="flex items-start gap-3 p-3.5 bg-ee-warning-bg border border-ee-warning-border rounded-ee-md text-ee-warning-fg text-sm"
        >
          <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" aria-hidden="true" />
          <div className="space-y-0.5">
            <p className="font-semibold">Franja de Alta Demanda</p>
            <p className="text-xs sm:text-sm leading-relaxed">
              Esta franja registra un alto volumen de cocina. Podrían producirse leves demoras en mostrador.
            </p>
          </div>
        </div>
      )}

      {/* Total Amount */}
      <div className="border-t border-ee-border-subtle pt-4 flex items-center justify-between gap-3">
        <span className="text-base font-semibold text-ee-text">Total a abonar</span>
        <span
          data-testid="checkout-summary-total"
          className="font-ee-mono tabular-nums text-2xl font-bold text-ee-text"
        >
          {formatPrice(calculatedTotal)}
        </span>
      </div>

      {action && <div className="pt-1">{action}</div>}
    </BentoCard>
  );
};
