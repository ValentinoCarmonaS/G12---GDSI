import React from 'react';

interface PaymentAlertBannerProps {
  type: 'error' | 'warning' | 'info';
  message: string;
  onClose?: () => void;
}

export const PaymentAlertBanner: React.FC<PaymentAlertBannerProps> = ({
  type,
  message,
  onClose,
}) => {
  if (!message) return null;

  const bgStyles =
    type === 'error'
      ? 'bg-rose-950/50 border-rose-600/50 text-rose-200'
      : type === 'warning'
      ? 'bg-amber-950/50 border-amber-600/50 text-amber-200'
      : 'bg-sky-950/50 border-sky-600/50 text-sky-200';

  return (
    <div
      role="alert"
      className={`p-4 rounded-xl border flex items-center justify-between gap-3 shadow-md my-4 ${bgStyles}`}
    >
      <div className="flex items-center gap-3">
        {type === 'error' && (
          <svg className="w-5 h-5 flex-shrink-0 text-rose-400" fill="currentColor" viewBox="0 0 20 20">
            <path
              fillRule="evenodd"
              d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
              clipRule="evenodd"
            />
          </svg>
        )}
        {type === 'warning' && (
          <svg className="w-5 h-5 flex-shrink-0 text-amber-400" fill="currentColor" viewBox="0 0 20 20">
            <path
              fillRule="evenodd"
              d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
              clipRule="evenodd"
            />
          </svg>
        )}
        {type === 'info' && (
          <svg className="w-5 h-5 flex-shrink-0 text-sky-400" fill="currentColor" viewBox="0 0 20 20">
            <path
              fillRule="evenodd"
              d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
              clipRule="evenodd"
            />
          </svg>
        )}
        <span className="text-sm font-medium">{message}</span>
      </div>

      {onClose && (
        <button
          type="button"
          onClick={onClose}
          aria-label="Cerrar alerta"
          className="text-neutral-400 hover:text-white transition-colors p-1"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      )}
    </div>
  );
};
