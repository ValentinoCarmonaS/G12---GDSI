import React from 'react';
import { PackageCheck } from 'lucide-react';

const FINAL_MESSAGE = 'Pedido Entregado — ¡Que disfrutes tu comida!';

export interface DeliveredOrderCalloutProps {
  ticketNumber: number | null;
  message?: string;
}

/**
 * Diner's terminal view once the counter confirms the delivery (spec 022, US2, RF-014, contract 03 §4).
 * It never shows the PIN (RF-015, edge case 12); the purchase summary is the page's own block.
 */
export const DeliveredOrderCallout: React.FC<DeliveredOrderCalloutProps> = ({ ticketNumber, message }) => {
  return (
    <div
      data-testid="delivered-order-callout"
      className="bg-emerald-700 text-white rounded-2xl p-8 text-center shadow-lg border-2 border-emerald-600 space-y-5"
    >
      <div className="inline-flex p-3 bg-white/20 rounded-full">
        <PackageCheck className="w-12 h-12 text-white" />
      </div>

      <h2 className="text-2xl font-bold tracking-tight">{message || FINAL_MESSAGE}</h2>

      {ticketNumber != null && (
        <div className="py-3 px-6 bg-white rounded-2xl shadow-inner inline-block">
          <span className="text-4xl font-black text-gray-900 tracking-wider">#{ticketNumber}</span>
        </div>
      )}
    </div>
  );
};
