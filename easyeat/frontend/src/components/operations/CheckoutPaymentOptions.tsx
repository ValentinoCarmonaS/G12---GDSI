import React, { useEffect, useState } from 'react';
import type { DinerPaymentMethod, PaymentMethod } from '../../types/operations';

export interface CheckoutPaymentOptionsProps {
  /** Only the methods the restaurant has enabled; the server already leaves the others out (INV-4). */
  methods: DinerPaymentMethod[];
  onConfirm: (method: PaymentMethod) => void | Promise<void>;
  /** Checks the chosen method again against the server at the moment of confirming (INV-9). */
  revalidate: (method: PaymentMethod) => Promise<void>;
  /** Asks the host to load the methods again, so a newly enabled one shows up (EC-011). */
  onRefresh: () => void;
}

const NO_METHODS_MESSAGE = 'No hay medios de pago online disponibles en este momento.';
const UNAVAILABLE_MESSAGE = 'El método de pago seleccionado ya no está disponible. Por favor elegí otro.';
const GENERIC_ERROR_MESSAGE = 'No se pudo verificar el método de pago. Intente nuevamente.';

const isConflict = (error: unknown): boolean =>
  typeof error === 'object' && error !== null && (error as { response?: { status?: number } }).response?.status === 409;

/**
 * Payment step of the diner checkout. It receives its data and its revalidation from the caller:
 * no orders module exists yet, so the wire-up lives in the integration document (T048).
 */
export const CheckoutPaymentOptions: React.FC<CheckoutPaymentOptionsProps> = ({
  methods,
  onConfirm,
  revalidate,
  onRefresh,
}) => {
  const [selected, setSelected] = useState<PaymentMethod | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isChecking, setIsChecking] = useState(false);

  useEffect(() => {
    setSelected((current) => (current && methods.some((method) => method.paymentMethod === current) ? current : null));
  }, [methods]);

  const handleConfirm = async () => {
    if (!selected) {
      return;
    }
    setError(null);
    setIsChecking(true);
    try {
      await revalidate(selected);
    } catch (failure) {
      if (isConflict(failure)) {
        setError(UNAVAILABLE_MESSAGE);
        setSelected(null);
        onRefresh();
      } else {
        setError(GENERIC_ERROR_MESSAGE);
      }
      setIsChecking(false);
      return;
    }
    setIsChecking(false);
    await onConfirm(selected);
  };

  return (
    <div className="space-y-4">
      {methods.length === 0 ? (
        <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          {NO_METHODS_MESSAGE}
        </p>
      ) : (
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium text-gray-700">Método de pago</legend>
          {methods.map((method) => (
            <label key={method.paymentMethod} className="flex items-center gap-2 text-sm text-gray-800">
              <input
                type="radio"
                name="payment-method"
                checked={selected === method.paymentMethod}
                onChange={() => {
                  setSelected(method.paymentMethod);
                  setError(null);
                }}
              />
              {method.displayName}
            </label>
          ))}
        </fieldset>
      )}

      {error && (
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          {error}
        </div>
      )}

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={handleConfirm}
          disabled={!selected || isChecking}
          className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm font-medium disabled:opacity-50"
        >
          {isChecking ? 'Verificando...' : 'Confirmar pedido'}
        </button>
        <button type="button" onClick={onRefresh} className="text-sm text-blue-700 underline">
          Actualizar opciones
        </button>
      </div>
    </div>
  );
};
