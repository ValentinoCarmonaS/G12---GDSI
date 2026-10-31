import React, { useEffect, useState } from 'react';
import { useRetailPriceCalculator } from '@/hooks/useRetailPriceCalculator';
import type { MarginSummary } from '@/types/dishReview';

export interface MarginPriceCalculatorProps {
  /** The dish's current margin reading (FR-012): the source of the server-computed disclosures
   * below, never recomputed here. */
  margin: MarginSummary;
  /**
   * Called only once the save is unblocked: immediately for a non-negative margin, or after the
   * Administrator confirms the advisory for a negative one (FR-014). Never called when the
   * Administrator abandons that advisory (FR-031).
   */
  onConfirm: (retailPrice: number, acknowledgeNegativeMargin: boolean) => void;
}

const pct = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/**
 * The bidirectional margin ↔ PVP calculator of FR-007, bound to the dish's current Food Cost.
 *
 * Two figures it never computes itself: {@code marginTolerancePp} and {@code
 * toleranceThresholdPrice} are read from the server's {@link MarginSummary} (T012's exact
 * BigDecimal arithmetic) rather than approximated here, because the client-side preview in {@link
 * useRetailPriceCalculator} exists for reactive typing feedback, not for the precision disclosure
 * SC-002 makes a requirement.
 */
export const MarginPriceCalculator: React.FC<MarginPriceCalculatorProps> = ({
  margin,
  onConfirm,
}) => {
  const calculator = useRetailPriceCalculator(margin.foodCost ?? 0);
  const [negativeMarginPending, setNegativeMarginPending] = useState(false);

  // Seeds the fields from the dish's current price once, on mount, already formatted to two
  // decimals. An effect rather than a lazy initializer: the seed calls the calculator's own
  // setters, and doing that synchronously during this component's first render would mean
  // updating a different hook's state while this one is still rendering.
  //
  // The onChange call alone would leave `editedField` set to whichever field it seeded,
  // disabling the other one forever — no blur ever follows a seed, so nothing would clear it.
  // The paired blur call clears `editedField` unconditionally, which does not depend on the
  // stale closure a same-tick onChange-then-blur pair has over the *value* it would otherwise
  // reformat; that reformatting step is itself redundant here because the seed is already
  // formatted to two decimals before onChange ever sees it.
  useEffect(() => {
    if (margin.retailPrice != null) {
      calculator.onPriceChange(margin.retailPrice.toFixed(2));
      calculator.onPriceBlur();
    } else if (margin.marginPct != null) {
      calculator.onMarginChange(margin.marginPct.toFixed(2));
      calculator.onMarginBlur();
    }
    // Intentionally run once: re-seeding on every prop change would overwrite what the
    // Administrator is actively typing.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const marginInputDisabled = calculator.editedField === 'price' || !margin.marginDerivable;
  const priceInputDisabled = calculator.editedField === 'margin';

  const discloseTolerance =
    margin.toleranceThresholdPrice != null &&
    calculator.retailPrice != null &&
    calculator.retailPrice < margin.toleranceThresholdPrice &&
    margin.marginTolerancePp != null;

  function handleSaveClick() {
    if (calculator.retailPrice == null) {
      return;
    }
    if (calculator.marginPct != null && calculator.marginPct < 0) {
      setNegativeMarginPending(true);
      return;
    }
    onConfirm(calculator.retailPrice, false);
  }

  function handleConfirmNegativeMargin() {
    if (calculator.retailPrice == null) {
      return;
    }
    setNegativeMarginPending(false);
    onConfirm(calculator.retailPrice, true);
  }

  function handleAbandonNegativeMargin() {
    // FR-031: abandoning touches nothing. The price input is left exactly as it was — no
    // field is reset, no callback fires, no audit entry is implied.
    setNegativeMarginPending(false);
  }

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <label className="block text-sm">
          <span className="text-gray-700">Margen comercial (%)</span>
          <input
            type="text"
            inputMode="decimal"
            aria-label="Margen comercial (%)"
            className="mt-1 block w-full rounded border border-gray-300 px-2 py-1 disabled:bg-gray-100 disabled:text-gray-400"
            value={calculator.marginInput}
            disabled={marginInputDisabled}
            onChange={(e) => calculator.onMarginChange(e.target.value)}
            onBlur={calculator.onMarginBlur}
          />
        </label>
        <label className="block text-sm">
          <span className="text-gray-700">Precio de venta (PVP)</span>
          <input
            type="text"
            inputMode="decimal"
            aria-label="Precio de venta (PVP)"
            className="mt-1 block w-full rounded border border-gray-300 px-2 py-1 disabled:bg-gray-100 disabled:text-gray-400"
            value={calculator.priceInput}
            disabled={priceInputDisabled}
            onChange={(e) => calculator.onPriceChange(e.target.value)}
            onBlur={calculator.onPriceBlur}
          />
        </label>
      </div>

      {!margin.marginDerivable && (
        <p className="text-xs text-gray-500">
          No se puede calcular un margen sin Food Cost: ingresá el precio de venta directamente.
        </p>
      )}

      {discloseTolerance && (
        <p data-testid="tolerance-disclosure" className="text-xs text-amber-700">
          A este precio, la precisión de redondeo alcanzable es de ±
          {pct.format(margin.marginTolerancePp as number)} puntos porcentuales.
        </p>
      )}

      {negativeMarginPending ? (
        <div className="rounded border border-amber-300 bg-amber-50 p-3 space-y-2">
          <p className="text-sm text-amber-800">
            El precio ingresado es menor al costo de producción: el margen resultante es negativo.
            ¿Confirmás que esta es la decisión comercial deseada?
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              className="rounded bg-amber-700 px-3 py-1 text-sm text-white"
              onClick={handleConfirmNegativeMargin}
            >
              Confirmar
            </button>
            <button
              type="button"
              className="rounded border border-gray-300 px-3 py-1 text-sm text-gray-700"
              onClick={handleAbandonNegativeMargin}
            >
              Cancelar
            </button>
          </div>
        </div>
      ) : (
        <button
          type="button"
          className="rounded bg-blue-700 px-3 py-1.5 text-sm text-white disabled:bg-gray-300"
          disabled={calculator.retailPrice == null}
          onClick={handleSaveClick}
        >
          Guardar
        </button>
      )}
    </div>
  );
};

export default MarginPriceCalculator;
