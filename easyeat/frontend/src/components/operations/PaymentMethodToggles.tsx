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
      <ul className="divide-y divide-gray-100">
        {methods.map((method) => (
          <li key={method.paymentMethod} className="flex items-center justify-between py-3">
            <span className="text-sm font-medium text-gray-800">{method.displayName}</span>
            <button
              type="button"
              role="switch"
              aria-checked={method.isEnabled}
              aria-label={method.displayName}
              disabled={pending !== null}
              onClick={() => handleToggle(method)}
              className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors disabled:opacity-50 ${
                method.isEnabled ? 'bg-blue-600' : 'bg-gray-300'
              }`}
            >
              <span
                className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                  method.isEnabled ? 'translate-x-6' : 'translate-x-1'
                }`}
              />
            </button>
          </li>
        ))}
      </ul>

      {noneEnabled && (
        <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          No hay ningún método de pago online habilitado: los comensales no podrán confirmar pedidos.
        </p>
      )}

      {error && (
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          {error}
          {conflict && onReload && (
            <button type="button" onClick={onReload} className="ml-3 underline font-medium">
              Recargar
            </button>
          )}
        </div>
      )}
    </div>
  );
};
