import React, { useState } from 'react';
import type { AdminPaymentMethod, PaymentMethod } from '../../types/operations';

export interface PaymentMethodTogglesProps {
  methods: AdminPaymentMethod[];
  /** `version` is null for a method that has no row yet. */
  onToggle: (method: PaymentMethod, enabled: boolean, version: number | null) => Promise<void>;
  onReload?: () => void;
}

const CONFLICT_MESSAGE =
  'La configuración fue modificada por otro administrador. Recargue la pantalla para ver los cambios.';
const GENERIC_ERROR_MESSAGE = 'No se pudo guardar el método de pago. Intente nuevamente.';

const isConflict = (error: unknown): boolean =>
  typeof error === 'object' && error !== null && (error as { response?: { status?: number } }).response?.status === 409;

export const PaymentMethodToggles: React.FC<PaymentMethodTogglesProps> = ({ methods, onToggle, onReload }) => {
  const [pending, setPending] = useState<PaymentMethod | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);

  const handleToggle = async (method: AdminPaymentMethod) => {
    setError(null);
    setConflict(false);
    setPending(method.paymentMethod);
    try {
      await onToggle(method.paymentMethod, !method.isEnabled, method.version);
    } catch (failure) {
      if (isConflict(failure)) {
        setError(CONFLICT_MESSAGE);
        setConflict(true);
      } else {
        setError(GENERIC_ERROR_MESSAGE);
      }
    } finally {
      setPending(null);
    }
  };

  const noneEnabled = methods.length > 0 && methods.every((method) => !method.isEnabled);

  return (
    <div className="space-y-4">
      <ul className="divide-y divide-[var(--ee-border)]">
        {methods.map((method) => (
          <li key={method.paymentMethod} className="flex items-center justify-between py-3 min-h-[44px]">
            <span className="text-sm font-medium text-[var(--ee-text)]">{method.displayName}</span>
            <button
              type="button"
              role="switch"
              aria-checked={method.isEnabled}
              aria-label={method.displayName}
              disabled={pending !== null}
              onClick={() => handleToggle(method)}
              className={`relative inline-flex h-7 w-12 items-center rounded-full transition-colors disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] ${
                method.isEnabled ? 'bg-[var(--ee-primary)]' : 'bg-[var(--ee-border)]'
              }`}
            >
              <span
                className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
                  method.isEnabled ? 'translate-x-6' : 'translate-x-1'
                }`}
              />
            </button>
          </li>
        ))}
      </ul>

      {noneEnabled && (
        <p className="rounded-[var(--ee-radius-md)] border border-[var(--ee-warning-border)] bg-[var(--ee-warning-bg)] px-3 py-2 text-sm text-[var(--ee-warning-text)]">
          No hay ningún método de pago online habilitado: los comensales no podrán confirmar pedidos.
        </p>
      )}

      {error && (
        <div role="alert" className="rounded-[var(--ee-radius-md)] border border-[var(--ee-danger-border)] bg-[var(--ee-danger-bg)] px-3 py-2 text-sm text-[var(--ee-danger-text)]">
          {error}
          {conflict && onReload && (
            <button type="button" onClick={onReload} className="ml-3 underline font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]">
              Recargar
            </button>
          )}
        </div>
      )}
    </div>
  );
};
