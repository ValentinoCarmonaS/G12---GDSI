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
      <h3 className="text-base font-semibold text-gray-900">Modalidad de entrega</h3>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Card: Lo antes posible (IMMEDIATE) */}
        <div
          onClick={() => {
            if (isImmediateAvailable) {
              onSelectMode('IMMEDIATE');
            }
          }}
          className={`relative border rounded-xl p-4 flex flex-col justify-between transition-all duration-150 ${
            !isImmediateAvailable
              ? 'opacity-60 bg-gray-50 border-gray-200 cursor-not-allowed'
              : selectedMode === 'IMMEDIATE'
              ? 'border-indigo-600 bg-indigo-50/50 ring-2 ring-indigo-500 shadow-sm cursor-pointer'
              : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50/50 cursor-pointer'
          }`}
        >
          <div className="flex items-start justify-between">
            <div className="flex items-center space-x-3">
              <input
                type="radio"
                id="mode-immediate"
                name="fulfillmentMode"
                value="IMMEDIATE"
                aria-label="Lo antes posible"
                checked={selectedMode === 'IMMEDIATE'}
                disabled={!isImmediateAvailable}
                onChange={() => isImmediateAvailable && onSelectMode('IMMEDIATE')}
                className="h-4 w-4 text-indigo-600 border-gray-300 focus:ring-indigo-500 cursor-pointer disabled:cursor-not-allowed"
              />
              <div className="p-2 bg-amber-100 text-amber-800 rounded-lg">
                <Zap className="w-5 h-5" />
              </div>
              <div>
                <label
                  htmlFor="mode-immediate"
                  className={`block text-sm font-semibold cursor-pointer ${
                    !isImmediateAvailable ? 'text-gray-400' : 'text-gray-900'
                  }`}
                >
                  Lo antes posible
                </label>
                <p className="text-xs text-gray-500 mt-0.5">Preparación inmediata en cocina</p>
              </div>
            </div>
          </div>

          {!isImmediateAvailable && immediateUnavailableReason && (
            <div className="mt-3 flex items-center space-x-2 text-xs text-amber-700 bg-amber-50 p-2 rounded-md border border-amber-200">
              <AlertTriangle className="w-4 h-4 flex-shrink-0 text-amber-600" />
              <span>{immediateUnavailableReason}</span>
            </div>
          )}
        </div>

        {/* Card: Para más tarde (SCHEDULED) */}
        <div
          onClick={() => onSelectMode('SCHEDULED')}
          className={`relative border rounded-xl p-4 flex flex-col justify-between transition-all duration-150 cursor-pointer ${
            selectedMode === 'SCHEDULED'
              ? 'border-indigo-600 bg-indigo-50/50 ring-2 ring-indigo-500 shadow-sm'
              : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50/50'
          }`}
        >
          <div className="flex items-start justify-between">
            <div className="flex items-center space-x-3">
              <input
                type="radio"
                id="mode-scheduled"
                name="fulfillmentMode"
                value="SCHEDULED"
                aria-label="Para más tarde"
                checked={selectedMode === 'SCHEDULED'}
                onChange={() => onSelectMode('SCHEDULED')}
                className="h-4 w-4 text-indigo-600 border-gray-300 focus:ring-indigo-500 cursor-pointer"
              />
              <div className="p-2 bg-blue-100 text-blue-800 rounded-lg">
                <Clock className="w-5 h-5" />
              </div>
              <div>
                <label
                  htmlFor="mode-scheduled"
                  className="block text-sm font-semibold text-gray-900 cursor-pointer"
                >
                  Para más tarde
                </label>
                <p className="text-xs text-gray-500 mt-0.5">Elegí día y horario de retiro</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
