import React from 'react';
import { Clock } from 'lucide-react';

interface PendingAccreditationBannerProps {
  orderId: string;
  message?: string;
  className?: string;
}

export const PendingAccreditationBanner: React.FC<PendingAccreditationBannerProps> = ({
  orderId,
  message,
  className = '',
}) => {
  return (
    <div
      role="alert"
      className={`bg-ee-warning-bg border border-ee-warning-border text-ee-warning-fg rounded-ee-md p-4 flex items-start gap-3 shadow-ee-elevated ${className}`}
    >
      <div className="shrink-0 mt-0.5">
        <Clock className="w-5 h-5" aria-hidden="true" />
      </div>
      <div className="flex-1 min-w-0">
        <h4 className="font-ee-display font-bold text-sm">Pago pendiente de confirmación</h4>
        <p className="text-sm mt-1 leading-relaxed">
          {message ||
            'Tu pago está siendo procesado por Mercado Pago. Tu reserva se encuentra congelada y el pedido pasará a preparación de inmediato al acreditarse.'}
        </p>
        <div className="text-xs mt-2 font-ee-mono tabular-nums font-semibold">
          Referencia de pedido: {orderId}
        </div>
      </div>
    </div>
  );
};
