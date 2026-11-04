import React, { useState, useEffect, useRef } from 'react';
import { AlertTriangle, Clock, XCircle } from 'lucide-react';

export interface PendingOrderCountdownProps {
  orderId: string;
  expiresAt: string; // ISO date string
  serverTime?: string; // ISO date string
  initialSeconds?: number;
  onCancel?: () => void | Promise<void>;
  onExpire?: () => void;
  isCancelling?: boolean;
}

export const PendingOrderCountdown: React.FC<PendingOrderCountdownProps> = ({
  orderId,
  expiresAt,
  serverTime,
  initialSeconds,
  onCancel,
  onExpire,
  isCancelling = false,
}) => {
  const calculateRemainingSeconds = (): number => {
    if (initialSeconds !== undefined) {
      return Math.max(0, initialSeconds);
    }
    const target = new Date(expiresAt).getTime();
    const now = serverTime ? new Date(serverTime).getTime() : Date.now();
    return Math.max(0, Math.floor((target - now) / 1000));
  };

  const [remainingSeconds, setRemainingSeconds] = useState<number>(calculateRemainingSeconds);
  const expiredTriggeredRef = useRef<boolean>(false);

  useEffect(() => {
    setRemainingSeconds(calculateRemainingSeconds());
    expiredTriggeredRef.current = false;
  }, [expiresAt, serverTime]);

  useEffect(() => {
    if (remainingSeconds <= 0) {
      if (!expiredTriggeredRef.current) {
        expiredTriggeredRef.current = true;
        if (onExpire) {
          onExpire();
        }
      }
      return;
    }

    const timer = setInterval(() => {
      setRemainingSeconds((prev) => {
        const next = prev - 1;
        if (next <= 0) {
          clearInterval(timer);
          if (!expiredTriggeredRef.current) {
            expiredTriggeredRef.current = true;
            if (onExpire) {
              onExpire();
            }
          }
          return 0;
        }
        return next;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [remainingSeconds, onExpire]);

  const formatTime = (totalSeconds: number): string => {
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  };

  const isExpired = remainingSeconds <= 0;
  const isUrgent = remainingSeconds > 0 && remainingSeconds < 120; // < 2 minutes

  return (
    <div className="bg-white rounded-2xl border border-gray-200 p-6 shadow-sm space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-gray-100 pb-5">
        <div className="flex items-center gap-3">
          <div
            className={`p-3 rounded-full ${
              isExpired
                ? 'bg-gray-100 text-gray-500'
                : isUrgent
                ? 'bg-amber-100 text-amber-600 animate-pulse'
                : 'bg-emerald-100 text-emerald-600'
            }`}
          >
            <Clock className="w-6 h-6" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-gray-900">
              {isExpired ? 'Reserva expirada' : 'Reserva en espera de pago'}
            </h2>
            <p className="text-xs text-gray-500 font-mono">Orden: #{orderId.slice(0, 8)}</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <div
            className={`px-4 py-2 rounded-xl text-2xl font-mono font-bold tracking-wider ${
              isExpired
                ? 'bg-gray-100 text-gray-400'
                : isUrgent
                ? 'bg-amber-50 text-amber-600 border border-amber-300 animate-pulse'
                : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
            }`}
          >
            {formatTime(remainingSeconds)}
          </div>
        </div>
      </div>

      {isUrgent && (
        <div
          data-testid="countdown-warning-banner"
          className="flex items-center gap-3 p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-sm animate-fade-in"
        >
          <AlertTriangle className="w-5 h-5 flex-shrink-0 text-amber-600" />
          <p className="font-medium">
            ¡Atención! Tu reserva está a punto de expirar. Completá el pago antes de que se libere tu cupo.
          </p>
        </div>
      )}

      {isExpired && (
        <div className="flex items-center gap-3 p-4 rounded-xl bg-gray-50 border border-gray-200 text-gray-700 text-sm">
          <XCircle className="w-5 h-5 flex-shrink-0 text-gray-500" />
          <p>El tiempo de espera de 15 minutos ha finalizado. La reserva ha expirado y el cupo fue liberado.</p>
        </div>
      )}

      {!isExpired && onCancel && (
        <div className="pt-2 flex justify-end">
          <button
            type="button"
            onClick={onCancel}
            disabled={isCancelling}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-rose-600 hover:text-rose-700 hover:bg-rose-50 rounded-xl transition-colors disabled:opacity-50"
          >
            <XCircle className="w-4 h-4" />
            {isCancelling ? 'Cancelando...' : 'Cancelar reserva'}
          </button>
        </div>
      )}
    </div>
  );
};
