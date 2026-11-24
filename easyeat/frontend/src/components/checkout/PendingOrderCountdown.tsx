import React, { useState, useEffect, useRef } from 'react';
import { AlertTriangle, Clock, XCircle } from 'lucide-react';
import { BentoCard } from '../diner/BentoCard';
import { Button } from '../primitives/Button';
import { FeedbackState } from '../primitives/FeedbackState';

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
    <BentoCard variant="elevated" padding="md" radius="lg" className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-ee-border-subtle pb-5">
        <div className="flex items-center gap-3">
          <div
            className={`p-3 rounded-ee-md border ${
              isExpired
                ? 'bg-ee-neutral-bg text-ee-neutral-fg border-ee-neutral-border'
                : isUrgent
                ? 'bg-ee-warning-bg text-ee-warning-fg border-ee-warning-border'
                : 'bg-ee-success-bg text-ee-success-fg border-ee-success-border'
            }`}
          >
            <Clock className="w-6 h-6" aria-hidden="true" />
          </div>
          <div>
            <h2 className="font-ee-display text-lg font-bold text-ee-text">
              {isExpired ? 'Reserva expirada' : 'Reserva en espera de pago'}
            </h2>
            <p className="text-xs text-ee-text-secondary font-ee-mono tabular-nums">
              Orden: #{orderId.slice(0, 8)}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <div
            data-testid="pending-order-timer"
            className={`px-4 py-2 rounded-ee-md text-2xl font-ee-mono tabular-nums font-bold tracking-wider border ${
              isExpired
                ? 'bg-ee-neutral-bg text-ee-neutral-fg border-ee-neutral-border'
                : isUrgent
                ? 'bg-ee-warning-bg text-ee-warning-fg border-ee-warning-border'
                : 'bg-ee-success-bg text-ee-success-fg border-ee-success-border'
            }`}
          >
            {formatTime(remainingSeconds)}
          </div>
        </div>
      </div>

      {isUrgent && (
        <FeedbackState
          variant="warning"
          data-testid="countdown-warning-banner"
          icon={<AlertTriangle className="w-5 h-5" />}
          message="¡Atención! Tu reserva está a punto de expirar. Completá el pago antes de que se libere tu cupo."
        />
      )}

      {isExpired && (
        <div className="flex items-center gap-3 p-4 rounded-ee-md bg-ee-neutral-bg border border-ee-neutral-border text-ee-neutral-fg text-sm">
          <XCircle className="w-5 h-5 shrink-0" aria-hidden="true" />
          <p>
            El tiempo de espera de 15 minutos ha finalizado. La reserva ha expirado y el cupo fue liberado.
          </p>
        </div>
      )}

      {!isExpired && onCancel && (
        <div className="pt-2 flex justify-end">
          <Button
            variant="danger"
            size="md"
            onClick={onCancel}
            disabled={isCancelling}
            loading={isCancelling}
            loadingText="Cancelando..."
            leftIcon={<XCircle className="w-4 h-4" />}
          >
            Cancelar reserva
          </Button>
        </div>
      )}
    </BentoCard>
  );
};
