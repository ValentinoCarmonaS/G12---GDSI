import React from 'react';
import { CheckCircle } from 'lucide-react';

export interface ReadyForPickupCalloutProps {
  ticketNumber: number;
  message?: string;
}

export const ReadyForPickupCallout: React.FC<ReadyForPickupCalloutProps> = ({
  ticketNumber,
  message,
}) => {
  return (
    <div
      data-testid="ready-for-pickup-callout"
      className="bg-green-600 text-white rounded-2xl p-8 text-center shadow-lg border-2 border-green-500"
    >
      <div className="inline-flex p-3 bg-white/20 rounded-full mb-3">
        <CheckCircle className="w-12 h-12 text-white" />
      </div>
      <h2 className="text-2xl font-bold tracking-tight">
        {message || '¡Tu pedido está listo para retirar!'}
      </h2>
      <p className="text-green-100 text-sm mt-1">
        Por favor, acercate al mostrador y presentá tu número de pedido:
      </p>
      <div className="mt-5 py-4 px-8 bg-white rounded-2xl shadow-inner inline-block">
        <span className="text-5xl font-black text-gray-900 tracking-wider">
          #{ticketNumber}
        </span>
      </div>
      <p className="text-xs text-green-200 mt-4">
        Presentá este comprobante en la zona de entrega de pedidos.
      </p>
    </div>
  );
};
