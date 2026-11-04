import React from 'react';
import { AlertTriangle, ShoppingBag } from 'lucide-react';
import { CartItem } from '../../types/cart';
import { FulfillmentMode, TimeSlotAvailability } from '../../types/order';

interface CheckoutSummaryProps {
  items: CartItem[];
  fulfillmentMode: FulfillmentMode | null;
  selectedDate?: string | null;
  selectedSlot?: TimeSlotAvailability | null;
  totalAmount?: number;
}

export const CheckoutSummary: React.FC<CheckoutSummaryProps> = ({
  items,
  fulfillmentMode,
  selectedDate,
  selectedSlot,
  totalAmount,
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
    <div className="bg-white rounded-2xl border border-gray-200 p-6 shadow-sm space-y-6">
      <div className="flex items-center gap-2 border-b border-gray-100 pb-4">
        <ShoppingBag className="w-5 h-5 text-emerald-600" />
        <h2 className="text-lg font-bold text-gray-900">Resumen del pedido</h2>
      </div>

      {/* Items list */}
      <div className="space-y-3">
        {items.length === 0 ? (
          <p className="text-sm text-gray-500 italic">No hay ítems en el carrito.</p>
        ) : (
          <ul className="divide-y divide-gray-100">
            {items.map((item) => (
              <li
                key={item.dishId}
                className="py-2.5 flex items-center justify-between text-sm"
              >
                <div className="flex items-center gap-2">
                  <span className="font-semibold text-gray-800">
                    {item.quantity}x
                  </span>
                  <span className="text-gray-700">{item.name}</span>
                </div>
                <span className="font-medium text-gray-900">
                  {formatPrice(item.price * item.quantity)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Fulfillment details */}
      <div className="bg-gray-50 rounded-xl p-4 space-y-2 border border-gray-100 text-sm">
        <div className="flex items-center justify-between">
          <span className="text-gray-500 font-medium">Modalidad:</span>
          <span className="font-semibold text-gray-800">
            {fulfillmentMode === 'IMMEDIATE'
              ? 'Lo antes posible (Inmediato)'
              : fulfillmentMode === 'SCHEDULED'
              ? 'Para más tarde (Turno programado)'
              : 'No seleccionada'}
          </span>
        </div>

        {fulfillmentMode === 'SCHEDULED' && (
          <>
            <div className="flex items-center justify-between">
              <span className="text-gray-500 font-medium">Fecha de retiro:</span>
              <span className="font-semibold text-gray-800">
                {selectedDate ? formatDate(selectedDate) : 'Pendiente de selección'}
              </span>
            </div>

            <div className="flex items-center justify-between">
              <span className="text-gray-500 font-medium">Horario de retiro:</span>
              <span className="font-semibold text-gray-800">
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
          className="flex items-start gap-3 p-3.5 bg-amber-50 border border-amber-200 rounded-xl text-amber-900 text-sm"
        >
          <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
          <div className="space-y-0.5">
            <p className="font-semibold text-amber-900">Franja de Alta Demanda</p>
            <p className="text-amber-800 text-xs sm:text-sm">
              Esta franja registra un alto volumen de cocina. Podrían producirse leves demoras en mostrador.
            </p>
          </div>
        </div>
      )}

      {/* Total Amount */}
      <div className="border-t border-gray-100 pt-4 flex items-center justify-between">
        <span className="text-base font-semibold text-gray-800">Total a abonar</span>
        <span className="text-2xl font-bold text-gray-900">
          {formatPrice(calculatedTotal)}
        </span>
      </div>
    </div>
  );
};
