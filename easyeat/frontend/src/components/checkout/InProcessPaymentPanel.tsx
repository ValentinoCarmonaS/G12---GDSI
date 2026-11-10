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
  const handleResume = () => {
    if (onResumePayment) {
      onResumePayment();
    } else if (redirectUrl) {
      window.location.href = redirectUrl;
    }
  };

  return (
    <div
      role="region"
      aria-label="Panel de pago en curso"
      className="p-6 bg-amber-950/40 border border-amber-600/40 rounded-xl shadow-lg my-4 text-white"
    >
      <div className="flex items-start gap-4">
        <div className="p-3 bg-amber-500/20 text-amber-400 rounded-lg flex-shrink-0">
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
          <h3 className="text-lg font-semibold text-amber-200">Pago en curso</h3>
          <p className="text-sm text-neutral-300 mt-1">
            Tenés un pago pendiente de confirmación con Mercado Pago. Podés retomar el pago en la pasarela o cancelar el intento para re-habilitar el checkout.
          </p>

          <div className="mt-4 flex flex-wrap gap-3">
            <button
              type="button"
              onClick={handleResume}
              className="px-4 py-2 bg-amber-500 hover:bg-amber-600 text-neutral-950 font-medium rounded-lg transition-colors text-sm shadow-md"
            >
              Retomar pago
            </button>

            <button
              type="button"
              onClick={onCancelAttempt}
              disabled={isCancelling}
              className="px-4 py-2 bg-neutral-800 hover:bg-neutral-700 text-neutral-200 border border-neutral-700 rounded-lg transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              {isCancelling ? (
                <>
                  <span className="w-4 h-4 border-2 border-neutral-400 border-t-transparent rounded-full animate-spin" />
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
