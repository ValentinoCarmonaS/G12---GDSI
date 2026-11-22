import React from 'react';
import { CheckCircle, ShieldCheck, Utensils } from 'lucide-react';
import { SecurityPinDisplay } from '../common/SecurityPinDisplay';
import { BentoCard } from '../diner';

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
    <BentoCard
      as="section"
      variant="hero"
      padding="lg"
      radius="lg"
      data-testid="ready-for-pickup-callout"
      data-bento-hero="pickup-pin"
      className="border-ee-success-border text-center space-y-6"
    >
      <div className="flex justify-center">
        <div
          className="inline-flex p-3 bg-ee-success-bg text-ee-success-fg border border-ee-success-border rounded-ee-md"
          aria-hidden="true"
        >
          <CheckCircle className="w-10 h-10" />
        </div>
      </div>

      <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-ee-text">
        {message || '¡Tu pedido está listo para retirar!'}
      </h2>

      {ticketNumber != null ? (
        <div className="space-y-3">
          <p className="text-sm text-ee-text-secondary">
            Por favor, acercate al mostrador y presentá tu número de pedido:
          </p>
          <div className="inline-block py-3 px-8 bg-ee-surface-subtle border border-ee-border-subtle rounded-ee-md">
            <span className="text-4xl sm:text-5xl font-ee-mono tabular-nums font-bold text-ee-text tracking-wider">
              #{ticketNumber}
            </span>
          </div>
        </div>
      ) : (
        <p className="text-sm text-ee-text-secondary">
          Por favor, acercate al mostrador para retirar tu pedido.
        </p>
      )}

      {securityCode && (
        <div className="bg-ee-success-bg border-2 border-ee-success-border rounded-ee-lg p-6 max-w-sm mx-auto text-center space-y-2">
          <div className="flex items-center justify-center gap-1.5 text-ee-success-fg text-xs font-ee-mono font-semibold uppercase tracking-wider">
            <ShieldCheck className="w-4 h-4 shrink-0" aria-hidden="true" />
            <span>Código de retiro</span>
          </div>
          <div className="py-1">
            <SecurityPinDisplay
              pin={securityCode}
              className="text-4xl sm:text-5xl font-ee-mono tabular-nums font-bold text-ee-success-fg"
            />
          </div>
          <p className="text-xs font-medium text-ee-success-fg">
            Dictale este código de 4 dígitos al personal de mostrador
          </p>
        </div>
      )}

      {items && items.length > 0 && (
        <div className="bg-ee-surface-subtle border border-ee-border-subtle rounded-ee-md p-4 max-w-md mx-auto text-left">
          <div className="flex items-center gap-1.5 text-ee-text text-xs font-semibold mb-2">
            <Utensils className="w-4 h-4 text-ee-text-secondary shrink-0" aria-hidden="true" />
            <span>Detalle del pedido para corroboración:</span>
          </div>
          <ul className="divide-y divide-ee-border-subtle text-sm text-ee-text">
            {items.map((item, idx) => (
              <li key={idx} className="py-1.5 flex justify-between items-center gap-2">
                <span className="truncate">{item.dishName}</span>
                <span className="font-ee-mono tabular-nums font-semibold text-ee-text shrink-0">
                  x{item.quantity}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <p className="text-xs text-ee-text-secondary">
        Presentá este comprobante en la zona de entrega de pedidos.
      </p>
    </BentoCard>
  );
};
