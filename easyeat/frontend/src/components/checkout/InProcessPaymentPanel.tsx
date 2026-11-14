import React from 'react';

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
      className="p-6 bg-amber-50 border border-amber-200 rounded-xl shadow-sm my-4 text-gray-900"
    >
      <div className="flex items-start gap-4">
        <div className="p-3 bg-amber-100 text-amber-700 rounded-lg flex-shrink-0">
          <svg
            className="w-6 h-6 animate-pulse"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
        </div>

        <div className="flex-1">
          <h3 className="text-lg font-semibold text-amber-950">Pago en curso</h3>
          <p className="text-sm text-gray-700 mt-1">
            {hasActivePreference
              ? 'Tenés un pago pendiente de confirmación con Mercado Pago. Podés retomar el pago en la pasarela o cancelar el intento para re-habilitar el checkout.'
              : 'Tenés un pago pendiente de confirmación, pero no existe preferencia de pago activa para retomar este intento. Cancelá el intento para liberar tu reserva y habilitar nuevamente el checkout.'}
          </p>

          <div className="mt-4 flex flex-wrap gap-3">
            {hasActivePreference && (
              <button
                type="button"
                onClick={handleResume}
                className="px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white font-semibold rounded-lg transition-colors text-sm shadow-sm"
              >
                Retomar pago
              </button>
            )}

            <button
              type="button"
              onClick={onCancelAttempt}
              disabled={isCancelling}
              className="px-4 py-2 bg-white hover:bg-gray-50 text-gray-800 border border-gray-300 font-medium rounded-lg transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              {isCancelling ? (
                <>
                  <span className="w-4 h-4 border-2 border-gray-500 border-t-transparent rounded-full animate-spin" />
                  Cancelando...
                </>
              ) : (
                'Cancelar intento'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
