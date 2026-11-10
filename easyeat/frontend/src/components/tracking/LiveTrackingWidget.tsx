import React from 'react';
import { Clock, AlertTriangle, ChefHat } from 'lucide-react';
import { useOrderLiveTrackingPolling } from '../../hooks/useOrderLiveTrackingPolling';
import { ScheduledSlotCountdown } from './ScheduledSlotCountdown';
import { ReadyForPickupCallout } from './ReadyForPickupCallout';
import type { FulfillmentMode, OrderLiveTrackingResponse } from '../../types/orderTracking';

export interface LiveTrackingWidgetProps {
  orderId: string;
  ticketNumber: number;
  fulfillmentMode: FulfillmentMode;
  timeSlotStart?: string | null;
  timeSlotEnd?: string | null;
  initialTracking?: OrderLiveTrackingResponse | null;
}

export const LiveTrackingWidget: React.FC<LiveTrackingWidgetProps> = ({
  orderId,
  ticketNumber,
  fulfillmentMode,
  timeSlotStart,
  timeSlotEnd,
  initialTracking,
}) => {
  const { tracking } = useOrderLiveTrackingPolling(orderId, initialTracking);

  const effectiveTicketNumber = tracking?.ticketNumber || ticketNumber;
  const isReady = tracking?.isReadyForPickup === true || tracking?.status === 'READY_FOR_PICKUP';

  // Terminal state: ready for pickup
  if (isReady) {
    return (
      <ReadyForPickupCallout
        ticketNumber={effectiveTicketNumber}
        message={tracking?.message}
      />
    );
  }

  // SCHEDULED mode
  if (fulfillmentMode === 'SCHEDULED') {
    return (
      <ScheduledSlotCountdown
        isCountdownActive={tracking?.isCountdownActive ?? false}
        timeSlotStart={timeSlotStart}
        timeSlotEnd={timeSlotEnd}
        estimatedMinutesRemaining={tracking?.estimatedMinutesRemaining}
      />
    );
  }

  // IMMEDIATE mode
  const estimatedMinutes = tracking?.estimatedMinutesRemaining ?? 20;
  const progress = tracking?.progressPercentage ?? 25;
  const saturation = tracking?.kdsQueueSaturation;
  const isHighDemandOrDelayed =
    saturation?.kitchenStatus === 'HIGH_DEMAND' || saturation?.kitchenStatus === 'DELAYED';

  return (
    <div className="bg-white border border-gray-100 rounded-2xl p-6 shadow-sm space-y-5">
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <div className="p-2.5 bg-primary-50 rounded-xl text-primary-600">
            <ChefHat className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-base font-bold text-gray-900">
              Preparando tu pedido
            </h3>
            <p className="text-xs text-gray-500">
              Pedido #{effectiveTicketNumber} • Retiro en mostrador
            </p>
          </div>
        </div>
        <div className="text-right">
          <div className="flex items-center space-x-1.5 text-primary-700">
            <Clock className="w-5 h-5 animate-pulse" />
            <span className="text-2xl font-black">{estimatedMinutes} min</span>
          </div>
          <p className="text-xs text-gray-500">Tiempo estimado</p>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="space-y-1.5">
        <div className="flex justify-between text-xs text-gray-500 font-medium">
          <span>Elaboración en cocina</span>
          <span>{progress}%</span>
        </div>
        <div
          role="progressbar"
          aria-valuenow={progress}
          aria-valuemin={0}
          aria-valuemax={100}
          className="w-full bg-gray-100 rounded-full h-3 overflow-hidden shadow-inner"
        >
          <div
            className="bg-primary-600 h-3 rounded-full transition-all duration-700 ease-out"
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>

      {/* Kitchen Saturation Notice */}
      {isHighDemandOrDelayed && (
        <div className="flex items-start space-x-2.5 p-3.5 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-xs">
          <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold">
              {tracking?.message ||
                (saturation?.kitchenStatus === 'HIGH_DEMAND'
                  ? 'Cocina con alta demanda'
                  : 'Demora adicional informada por cocina')}
            </p>
            <p className="text-amber-700 mt-0.5">
              Hay {saturation?.activeOrdersAhead ?? 0} pedidos por delante en cola.
            </p>
          </div>
        </div>
      )}
    </div>
  );
};
