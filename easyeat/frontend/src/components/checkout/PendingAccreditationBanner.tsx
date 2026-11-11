import React from 'react';

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
      className={`bg-amber-500/10 border border-amber-500/30 text-amber-200 rounded-xl p-4 flex items-start space-x-3 shadow-lg ${className}`}
    >
      <div className="flex-shrink-0 mt-0.5">
        <svg
          className="w-5 h-5 text-amber-400 animate-spin"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle
            className="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            strokeWidth="4"
          ></circle>
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          ></path>
        </svg>
      </div>
      <div className="flex-1">
        <h4 className="font-semibold text-amber-300">Pago pendiente de confirmación</h4>
        <p className="text-sm mt-1 text-amber-200/90">
          {message ||
            'Tu pago está siendo procesado por Mercado Pago. Tu reserva se encuentra congelada y el pedido pasará a preparación de inmediato al acreditarse.'}
        </p>
        <div className="text-xs text-amber-300/70 mt-2 font-mono">
          Referencia de pedido: {orderId}
        </div>
      </div>
    </div>
  );
};
