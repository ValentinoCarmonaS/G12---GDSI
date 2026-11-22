import React from 'react';
import { Clock } from 'lucide-react';
import { Button } from '../primitives/Button';
import { StatusBadge } from '../primitives/StatusBadge';

interface InProcessPaymentPanelProps {
  redirectUrl?: string | null;
  onResumePayment?: () => void;
  onCancelAttempt?: () => void;
  isCancelling?: boolean;
}

export const InProcessPaymentPanel: React.FC<InProcessPaymentPanelProps> = ({
  redirectUrl,
  onResumePayment,
  onCancelAttempt,
  isCancelling = false,
}) => {
  const normalizedRedirectUrl = redirectUrl?.trim() || null;
  const hasActivePreference = Boolean(normalizedRedirectUrl);

  const handleResume = () => {
    if (!hasActivePreference) {
      return;
    }
    if (onResumePayment) {
      onResumePayment();
    } else if (normalizedRedirectUrl) {
      window.location.href = normalizedRedirectUrl;
    }
  };

  return (
    <div
      role="region"
      aria-label="Panel de pago en curso"
      data-testid="in-process-payment-panel"
      className="p-6 bg-ee-warning-bg border border-ee-warning-border rounded-ee-lg shadow-ee-elevated text-ee-warning-fg"
    >
      <div className="flex items-start gap-4">
        <div className="p-2.5 bg-ee-surface-elevated border border-ee-warning-border text-ee-warning-fg rounded-ee-sm shrink-0">
          <Clock className="w-5 h-5" aria-hidden="true" />
        </div>

        <div className="flex-1 min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-ee-display text-lg font-bold">Pago en curso</h3>
            <StatusBadge variant="warning">Pendiente</StatusBadge>
          </div>
          <p className="text-sm leading-relaxed">
            {hasActivePreference
              ? 'Tenés un pago pendiente de confirmación con Mercado Pago. Podés retomar el pago en la pasarela o cancelar el intento para re-habilitar el checkout.'
              : 'Tenés un pago pendiente de confirmación, pero no existe preferencia de pago activa para retomar este intento. Cancelá el intento para liberar tu reserva y habilitar nuevamente el checkout.'}
          </p>

          <div className="pt-2 flex flex-wrap gap-3">
            {hasActivePreference && (
              <Button variant="primary" size="md" onClick={handleResume}>
                Retomar pago
              </Button>
            )}

            <Button
              variant="outline"
              size="md"
              onClick={onCancelAttempt}
              disabled={isCancelling}
              loading={isCancelling}
              loadingText="Cancelando..."
            >
              Cancelar intento
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};
