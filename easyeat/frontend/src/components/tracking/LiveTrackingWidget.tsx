import React from 'react';
import { Clock, AlertTriangle, ChefHat, Users, Check } from 'lucide-react';
import { useOrderLiveTrackingPolling } from '../../hooks/useOrderLiveTrackingPolling';
import { ScheduledSlotCountdown } from './ScheduledSlotCountdown';
import { ReadyForPickupCallout } from './ReadyForPickupCallout';
import { DeliveredOrderCallout } from './DeliveredOrderCallout';
import { ConnectionStatusBadge } from '../common/ConnectionStatusBadge';
import { BentoCard } from '../diner';
import { StatusBadge } from '../primitives';
import type { FulfillmentMode, OrderLiveTrackingResponse } from '../../types/orderTracking';

export interface LiveTrackingWidgetProps {
  orderId: string;
  ticketNumber: number | null;
  fulfillmentMode: FulfillmentMode;
  timeSlotStart?: string | null;
  timeSlotEnd?: string | null;
  initialTracking?: OrderLiveTrackingResponse | null;
}

const TRACKING_STAGES = [
  { id: 'confirmed', label: 'Confirmado' },
  { id: 'preparing', label: 'En preparación' },
  { id: 'ready', label: 'Listo' },
  { id: 'delivered', label: 'Entregado' },
] as const;

const TrackingStageStepper: React.FC<{ activeIndex: number }> = ({ activeIndex }) => (
  <BentoCard variant="subtle" padding="sm" radius="md" data-testid="tracking-stage-stepper">
    <ol
      aria-label="Progreso del pedido"
      className="grid grid-cols-2 sm:grid-cols-4 gap-2"
    >
      {TRACKING_STAGES.map((stage, idx) => {
        const isCompleted = idx < activeIndex;
        const isCurrent = idx === activeIndex;
        return (
          <li
            key={stage.id}
            aria-current={isCurrent ? 'step' : undefined}
            className={`flex items-center gap-2 rounded-ee-sm px-3 py-2 text-xs font-semibold border transition-colors duration-150 motion-reduce:transition-none ${
              isCompleted
                ? 'bg-ee-success-bg text-ee-success-fg border-ee-success-border'
                : isCurrent
                  ? 'bg-ee-primary text-ee-on-primary border-ee-primary-border'
                  : 'bg-ee-surface-elevated text-ee-text-secondary border-ee-border-subtle'
            }`}
          >
            <span
              aria-hidden="true"
              className={`w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-ee-mono tabular-nums shrink-0 border ${
                isCompleted
                  ? 'bg-ee-success-fg text-ee-surface-elevated border-ee-success-fg'
                  : isCurrent
                    ? 'bg-ee-surface-elevated text-ee-text border-ee-primary-border'
                    : 'bg-ee-surface-subtle text-ee-text-secondary border-ee-border-subtle'
              }`}
            >
              {isCompleted ? <Check className="w-3 h-3" /> : idx + 1}
            </span>
            <span className="truncate">{stage.label}</span>
          </li>
        );
      })}
    </ol>
  </BentoCard>
);

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

  // Spec 022 terminal state: delivered at the counter; no PIN and no further polling (RF-014, RF-015).
  if (tracking?.status === 'COMPLETED') {
    return (
      <div className="space-y-4">
        <TrackingStageStepper activeIndex={3} />
        <DeliveredOrderCallout ticketNumber={effectiveTicketNumber} message={tracking.message} />
      </div>
    );
  }

  // Ready for pickup: the PIN is shown while the diner walks to the counter
  if (isReady) {
    return (
      <div className="space-y-4">
        <TrackingStageStepper activeIndex={2} />
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
        <TrackingStageStepper activeIndex={tracking?.isCountdownActive ? 1 : 0} />
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
        <TrackingStageStepper activeIndex={0} />
        {isDisconnected && (
          <div className="flex justify-end">
            <ConnectionStatusBadge isDisconnected={isDisconnected} />
          </div>
        )}
        <BentoCard variant="elevated" padding="md" radius="lg" className="space-y-5">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-3 min-w-0">
              <div
                className="p-2.5 bg-ee-warning-bg border border-ee-warning-border rounded-ee-sm text-ee-warning-fg shrink-0"
                aria-hidden="true"
              >
                <Users className="w-6 h-6" />
              </div>
              <div className="min-w-0">
                <div className="mb-1">
                  <StatusBadge variant="warning">
                    {tracking?.message || 'En cola en cocina'}
                  </StatusBadge>
                </div>
                <p className="text-xs text-ee-text-secondary">
                  {effectiveTicketNumber != null
                    ? `Pedido #${effectiveTicketNumber} • Retiro en mostrador`
                    : 'Retiro en mostrador'}
                </p>
              </div>
            </div>
          </div>

          <div className="p-4 bg-ee-warning-bg border border-ee-warning-border rounded-ee-md text-center">
            <p className="text-base font-bold text-ee-warning-fg">
              Tenés {tracking.positionAhead} pedidos por delante
            </p>
            <p className="text-xs text-ee-warning-fg mt-1">
              Tu comanda se encuentra registrada. En breve el equipo de cocina comenzará la preparación.
            </p>
          </div>
        </BentoCard>
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
      <TrackingStageStepper activeIndex={1} />
      {isDisconnected && (
        <div className="flex justify-end">
          <ConnectionStatusBadge isDisconnected={isDisconnected} />
        </div>
      )}
      <BentoCard variant="elevated" padding="md" radius="lg" className="space-y-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            <div
              className="p-2.5 bg-ee-success-bg border border-ee-success-border rounded-ee-sm text-ee-success-fg shrink-0"
              aria-hidden="true"
            >
              <ChefHat className="w-6 h-6" />
            </div>
            <div className="min-w-0">
              <h3 className="text-base font-bold text-ee-text">
                {tracking?.message || 'Preparando tu pedido'}
              </h3>
              <p className="text-xs text-ee-text-secondary">
                {effectiveTicketNumber != null
                  ? `Pedido #${effectiveTicketNumber} • Retiro en mostrador`
                  : 'Retiro en mostrador'}
              </p>
            </div>
          </div>
          <div className="sm:text-right shrink-0">
            <div className="inline-flex items-center gap-1.5 text-ee-success-fg">
              <Clock className="w-5 h-5" aria-hidden="true" />
              <span className="text-2xl font-ee-mono tabular-nums font-bold">
                {estimatedMinutes} min
              </span>
            </div>
            <p className="text-xs text-ee-text-secondary">Tiempo estimado</p>
          </div>
        </div>

        {/* Progress Bar */}
        <div className="space-y-1.5">
          <div className="flex justify-between text-xs text-ee-text-secondary font-medium">
            <span>Elaboración en cocina</span>
            <span className="font-ee-mono tabular-nums font-bold text-ee-success-fg">
              {progress}%
            </span>
          </div>
          <div
            role="progressbar"
            aria-valuenow={progress}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label="Progreso de elaboración en cocina"
            className="w-full bg-ee-surface-subtle border border-ee-border-subtle rounded-full h-3.5 overflow-hidden"
          >
            <div
              className="bg-ee-success-border h-full rounded-full transition-[width] duration-200 motion-reduce:transition-none"
              style={{ width: `${Math.min(100, Math.max(0, progress))}%` }}
            />
          </div>
        </div>

        {/* Kitchen Saturation / Delay Notice */}
        {isHighDemandOrDelayed && (
          <div
            data-testid="tracking-delay-notice"
            role="status"
            aria-live="polite"
            className="flex items-start gap-2.5 p-3.5 bg-ee-warning-bg border border-ee-warning-border rounded-ee-md text-ee-warning-fg text-xs"
          >
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden="true" />
            <div>
              <p className="font-semibold">
                {saturation?.globalDelayAppliedMinutes && saturation.globalDelayAppliedMinutes > 0
                  ? `Demora operativa en cocina (+${saturation.globalDelayAppliedMinutes} min)`
                  : 'Cocina con alta demanda'}
              </p>
              <p className="mt-0.5">
                {(saturation?.activeOrdersAhead ?? 0) > 0
                  ? `El equipo está elaborando ${saturation?.activeOrdersAhead} ${
                      saturation?.activeOrdersAhead === 1 ? 'pedido' : 'pedidos'
                    } por delante en cola.`
                  : 'El equipo de cocina extendió el tiempo de preparación para asegurar la calidad de tu pedido.'}
              </p>
            </div>
          </div>
        )}
      </BentoCard>
    </div>
  );
};
