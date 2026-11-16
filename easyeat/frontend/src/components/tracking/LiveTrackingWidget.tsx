import React from 'react';
import { Clock, AlertTriangle, ChefHat, Users } from 'lucide-react';
import { useOrderLiveTrackingPolling } from '../../hooks/useOrderLiveTrackingPolling';
import { ScheduledSlotCountdown } from './ScheduledSlotCountdown';
import { ReadyForPickupCallout } from './ReadyForPickupCallout';
import { ConnectionStatusBadge } from '../common/ConnectionStatusBadge';
import type { FulfillmentMode, OrderLiveTrackingResponse } from '../../types/orderTracking';

export interface LiveTrackingWidgetProps {
  orderId: string;
  ticketNumber: number | null;
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
  const { tracking, isDisconnected } = useOrderLiveTrackingPolling(orderId, initialTracking);

  const effectiveTicketNumber = tracking?.ticketNumber ?? ticketNumber ?? null;
  const isReady = tracking?.isReadyForPickup === true || tracking?.status === 'READY_FOR_PICKUP';

  // Terminal state: ready for pickup
  if (isReady) {
    return (
      <div className="space-y-4">
        {isDisconnected && (
          <div className="flex justify-end">
            <ConnectionStatusBadge isDisconnected={isDisconnected} />
          </div>
        )}
        <ReadyForPickupCallout
          ticketNumber={effectiveTicketNumber}
          message={tracking?.message}
          securityCode={tracking?.securityCode}
          items={tracking?.items}
        />
      </div>
    );
  }

  // SCHEDULED mode
  if (fulfillmentMode === 'SCHEDULED') {
    return (
      <div className="space-y-4">
        {isDisconnected && (
          <div className="flex justify-end">
            <ConnectionStatusBadge isDisconnected={isDisconnected} />
          </div>
        )}
        <ScheduledSlotCountdown
          isCountdownActive={tracking?.isCountdownActive ?? false}
          timeSlotStart={timeSlotStart}
          timeSlotEnd={timeSlotEnd}
          estimatedMinutesRemaining={tracking?.estimatedMinutesRemaining}
          message={tracking?.message}
        />
      </div>
    );
  }

  // QUEUED state (order is in kitchen queue)
  const isQueued = tracking?.positionAhead != null;
  if (isQueued) {
    return (
      <div className="space-y-4">
        {isDisconnected && (
          <div className="flex justify-end">
            <ConnectionStatusBadge isDisconnected={isDisconnected} />
          </div>
        )}
        <div className="bg-white border border-gray-100 rounded-2xl p-6 shadow-sm space-y-5">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <div className="p-2.5 bg-amber-50 rounded-xl text-amber-600">
                <Users className="w-6 h-6" />
              </div>
              <div>
                <div className="inline-flex items-center gap-2 mb-1">
                  <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-800">
                    {tracking?.message || 'En cola en cocina'}
                  </span>
                </div>
                <p className="text-xs text-gray-500">
                  {effectiveTicketNumber != null
                    ? `Pedido #${effectiveTicketNumber} • Retiro en mostrador`
                    : 'Retiro en mostrador'}
                </p>
              </div>
            </div>
          </div>

          <div className="p-4 bg-amber-50/70 border border-amber-200/60 rounded-xl text-center">
            <p className="text-base font-bold text-amber-900">
              Tenés {tracking.positionAhead} pedidos por delante
            </p>
            <p className="text-xs text-amber-700 mt-1">
              Tu comanda se encuentra registrada. En breve el equipo de cocina comenzará la preparación.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // IMMEDIATE mode (IN_PREPARATION or active cooking)
  const estimatedMinutes = tracking?.remainingMinutes ?? tracking?.estimatedMinutesRemaining ?? 20;
  const progress = tracking?.progressPercent ?? tracking?.progressPercentage ?? 25;
  const saturation = tracking?.kdsQueueSaturation;
  const isHighDemandOrDelayed =
    saturation?.kitchenStatus === 'HIGH_DEMAND' || saturation?.kitchenStatus === 'DELAYED';

  return (
    <div className="space-y-4">
      {isDisconnected && (
        <div className="flex justify-end">
          <ConnectionStatusBadge isDisconnected={isDisconnected} />
        </div>
      )}
      <div className="bg-white border border-gray-100 rounded-2xl p-6 shadow-sm space-y-5">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="p-2.5 bg-emerald-50 rounded-xl text-emerald-600">
              <ChefHat className="w-6 h-6" />
            </div>
            <div>
              <h3 className="text-base font-bold text-gray-900">
                {tracking?.message || 'Preparando tu pedido'}
              </h3>
              <p className="text-xs text-gray-500">
                {effectiveTicketNumber != null
                  ? `Pedido #${effectiveTicketNumber} • Retiro en mostrador`
                  : 'Retiro en mostrador'}
              </p>
            </div>
          </div>
          <div className="text-right">
            <div className="flex items-center space-x-1.5 text-emerald-700">
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
            <span className="font-bold text-emerald-700">{progress}%</span>
          </div>
          <div
            role="progressbar"
            aria-valuenow={progress}
            aria-valuemin={0}
            aria-valuemax={100}
            className="w-full bg-gray-200 rounded-full h-3.5 overflow-hidden shadow-inner"
          >
            <div
              className="bg-emerald-600 h-full rounded-full transition-all duration-700 ease-out"
              style={{ width: `${Math.min(100, Math.max(0, progress))}%` }}
            />
          </div>
        </div>

        {/* Kitchen Saturation Notice */}
        {isHighDemandOrDelayed && (
          <div className="flex items-start space-x-2.5 p-3.5 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-xs">
            <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">
                {saturation?.globalDelayAppliedMinutes && saturation.globalDelayAppliedMinutes > 0
                  ? `Demora operativa en cocina (+${saturation.globalDelayAppliedMinutes} min)`
                  : 'Cocina con alta demanda'}
              </p>
              <p className="text-amber-700 mt-0.5">
                {(saturation?.activeOrdersAhead ?? 0) > 0
                  ? `El equipo está elaborando ${saturation?.activeOrdersAhead} ${
                      saturation?.activeOrdersAhead === 1 ? 'pedido' : 'pedidos'
                    } por delante en cola.`
                  : 'El equipo de cocina extendió el tiempo de preparación para asegurar la calidad de tu pedido.'}
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
