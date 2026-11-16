import React from 'react';
import { CheckCircle, ShieldCheck, Utensils } from 'lucide-react';

export interface ReadyForPickupItem {
  dishName: string;
  quantity: number;
}

export interface ReadyForPickupCalloutProps {
  ticketNumber: number | null;
  message?: string;
  securityCode?: string | null;
  items?: ReadyForPickupItem[];
}

export const ReadyForPickupCallout: React.FC<ReadyForPickupCalloutProps> = ({
  ticketNumber,
  message,
  securityCode,
  items,
}) => {
  return (
    <div
      data-testid="ready-for-pickup-callout"
      className="bg-green-600 text-white rounded-2xl p-8 text-center shadow-lg border-2 border-green-500 space-y-6"
    >
      <div className="inline-flex p-3 bg-white/20 rounded-full">
        <CheckCircle className="w-12 h-12 text-white" />
      </div>

      <h2 className="text-2xl font-bold tracking-tight">
        {message || '¡Tu pedido está listo para retirar!'}
      </h2>

      {ticketNumber != null ? (
        <div>
          <p className="text-green-100 text-sm mt-1">
            Por favor, acercate al mostrador y presentá tu número de pedido:
          </p>
          <div className="mt-4 py-4 px-8 bg-white rounded-2xl shadow-inner inline-block">
            <span className="text-5xl font-black text-gray-900 tracking-wider">
              #{ticketNumber}
            </span>
          </div>
        </div>
      ) : (
        <p className="text-green-100 text-sm mt-1">
          Por favor, acercate al mostrador para retirar tu pedido.
        </p>
      )}

      {securityCode && (
        <div className="bg-green-700/60 border border-green-400/40 rounded-xl p-4 max-w-xs mx-auto text-center">
          <div className="flex items-center justify-center gap-1.5 text-green-100 text-xs font-semibold mb-1">
            <ShieldCheck className="w-4 h-4 text-green-200" />
            <span>Código de retiro</span>
          </div>
          <span className="text-3xl font-black tracking-widest text-white font-mono">
            {securityCode}
          </span>
          <p className="text-[11px] text-green-200 mt-1">
            Dictale este código de 4 dígitos al personal de mostrador
          </p>
        </div>
      )}

      {items && items.length > 0 && (
        <div className="bg-green-700/50 border border-green-400/30 rounded-xl p-4 max-w-md mx-auto text-left">
          <div className="flex items-center gap-1.5 text-green-100 text-xs font-semibold mb-2">
            <Utensils className="w-4 h-4 text-green-200" />
            <span>Detalle del pedido para corroboración:</span>
          </div>
          <ul className="divide-y divide-green-500/40 text-sm text-green-50">
            {items.map((item, idx) => (
              <li key={idx} className="py-1.5 flex justify-between items-center">
                <span>{item.dishName}</span>
                <span className="font-bold text-white ml-2">x{item.quantity}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <p className="text-xs text-green-200">
        Presentá este comprobante en la zona de entrega de pedidos.
      </p>
    </div>
  );
};
