import React from 'react';
import { Clock, Zap, AlertTriangle } from 'lucide-react';
import { FulfillmentMode } from '../../types/order';

export interface FulfillmentModeSelectorProps {
  selectedMode: FulfillmentMode | null;
  onSelectMode: (mode: FulfillmentMode) => void;
  isImmediateAvailable?: boolean;
  immediateUnavailableReason?: string;
}

/**
 * FulfillmentModeSelector: Mutually exclusive radio card selector for checkout (US1, FR-001, FR-004).
 * Diners can select between:
 * - "Lo antes posible" (IMMEDIATE): Disabled when kitchen capacity is at 100% or outside operating hours.
 * - "Para más tarde" (SCHEDULED): Opens date and 15-minute slot selection grid.
 */
export const FulfillmentModeSelector: React.FC<FulfillmentModeSelectorProps> = ({
  selectedMode,
  onSelectMode,
  isImmediateAvailable = true,
  immediateUnavailableReason = 'Cocina al límite de capacidad actual — Seleccioná una reserva programada',
}) => {
  return (
    <div className="w-full space-y-4" role="radiogroup" aria-label="Modalidad de retiro">
      <h3 className="font-ee-display text-base font-bold text-ee-text">Modalidad de entrega</h3>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Card: Lo antes posible (IMMEDIATE) */}
        <div
          onClick={() => {
            if (isImmediateAvailable) {
              onSelectMode('IMMEDIATE');
            }
          }}
          className={`relative min-h-[44px] rounded-ee-md border p-4 flex flex-col justify-between transition-colors duration-150 motion-reduce:transition-none focus-within:ring-2 focus-within:ring-ee-focus-ring ${
            !isImmediateAvailable
              ? 'opacity-60 bg-ee-surface-subtle border-ee-border-subtle cursor-not-allowed'
              : selectedMode === 'IMMEDIATE'
              ? 'border-ee-primary-border bg-ee-primary/30 shadow-ee-elevated cursor-pointer'
              : 'border-ee-border-subtle bg-ee-surface-elevated hover:border-ee-border-strong hover:bg-ee-surface-subtle cursor-pointer'
          }`}
        >
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-3">
              <input
                type="radio"
                id="mode-immediate"
                name="fulfillmentMode"
                value="IMMEDIATE"
                aria-label="Lo antes posible"
                checked={selectedMode === 'IMMEDIATE'}
                disabled={!isImmediateAvailable}
                onChange={() => isImmediateAvailable && onSelectMode('IMMEDIATE')}
                className="h-5 w-5 accent-ee-primary-border focus-visible:outline-2 focus-visible:outline-ee-focus-ring cursor-pointer disabled:cursor-not-allowed"
              />
              <div className="p-2.5 bg-ee-primary text-ee-on-primary border border-ee-primary-border/30 rounded-ee-sm shrink-0">
                <Zap className="w-5 h-5" aria-hidden="true" />
              </div>
              <div>
                <label
                  htmlFor="mode-immediate"
                  className={`block text-sm font-semibold cursor-pointer ${
                    !isImmediateAvailable ? 'text-ee-text-secondary cursor-not-allowed' : 'text-ee-text'
                  }`}
                >
                  Lo antes posible
                </label>
                <p className="text-xs text-ee-text-secondary mt-0.5">Preparación inmediata en cocina</p>
              </div>
            </div>
          </div>

          {!isImmediateAvailable && immediateUnavailableReason && (
            <div className="mt-3 flex items-center gap-2 text-xs text-ee-warning-fg bg-ee-warning-bg p-2.5 rounded-ee-sm border border-ee-warning-border">
              <AlertTriangle className="w-4 h-4 shrink-0" aria-hidden="true" />
              <span>{immediateUnavailableReason}</span>
            </div>
          )}
        </div>

        {/* Card: Para más tarde (SCHEDULED) */}
        <div
          onClick={() => onSelectMode('SCHEDULED')}
          className={`relative min-h-[44px] rounded-ee-md border p-4 flex flex-col justify-between transition-colors duration-150 motion-reduce:transition-none focus-within:ring-2 focus-within:ring-ee-focus-ring cursor-pointer ${
            selectedMode === 'SCHEDULED'
              ? 'border-ee-primary-border bg-ee-primary/30 shadow-ee-elevated'
              : 'border-ee-border-subtle bg-ee-surface-elevated hover:border-ee-border-strong hover:bg-ee-surface-subtle'
          }`}
        >
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-3">
              <input
                type="radio"
                id="mode-scheduled"
                name="fulfillmentMode"
                value="SCHEDULED"
                aria-label="Para más tarde"
                checked={selectedMode === 'SCHEDULED'}
                onChange={() => onSelectMode('SCHEDULED')}
                className="h-5 w-5 accent-ee-primary-border focus-visible:outline-2 focus-visible:outline-ee-focus-ring cursor-pointer"
              />
              <div className="p-2.5 bg-ee-secondary text-ee-on-secondary border border-ee-secondary-border/30 rounded-ee-sm shrink-0">
                <Clock className="w-5 h-5" aria-hidden="true" />
              </div>
              <div>
                <label
                  htmlFor="mode-scheduled"
                  className="block text-sm font-semibold text-ee-text cursor-pointer"
                >
                  Para más tarde
                </label>
                <p className="text-xs text-ee-text-secondary mt-0.5">Elegí día y horario de retiro</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
