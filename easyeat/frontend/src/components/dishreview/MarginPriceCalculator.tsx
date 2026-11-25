import React, { useEffect, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { useRetailPriceCalculator } from '@/hooks/useRetailPriceCalculator';
import { Button } from '@/components/primitives';
import type { MarginSummary } from '@/types/dishReview';

export interface MarginPriceCalculatorProps {
  margin: MarginSummary;
  onConfirm: (retailPrice: number, acknowledgeNegativeMargin: boolean) => void;
}

const pct = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export const MarginPriceCalculator: React.FC<MarginPriceCalculatorProps> = ({
  margin,
  onConfirm,
}) => {
  const calculator = useRetailPriceCalculator(margin.foodCost ?? 0);
  const [negativeMarginPending, setNegativeMarginPending] = useState(false);

  useEffect(() => {
    if (margin.retailPrice != null) {
      calculator.onPriceChange(margin.retailPrice.toFixed(2));
      calculator.onPriceBlur();
    } else if (margin.marginPct != null) {
      calculator.onMarginChange(margin.marginPct.toFixed(2));
      calculator.onMarginBlur();
    }
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
    setNegativeMarginPending(false);
  }

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <label className="block text-sm">
          <span className="font-medium text-ee-text">Margen comercial (%)</span>
          <input
            type="text"
            inputMode="decimal"
            aria-label="Margen comercial (%)"
            className="mt-1 block w-full min-h-[44px] rounded-ee-sm border border-ee-border-strong bg-ee-surface-elevated px-3 py-2 font-ee-mono tabular-nums text-sm text-ee-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring disabled:bg-ee-surface-subtle disabled:text-ee-text-secondary"
            value={calculator.marginInput}
            disabled={marginInputDisabled}
            onChange={(e) => calculator.onMarginChange(e.target.value)}
            onBlur={calculator.onMarginBlur}
          />
        </label>
        <label className="block text-sm">
          <span className="font-medium text-ee-text">Precio de venta (PVP)</span>
          <input
            type="text"
            inputMode="decimal"
            aria-label="Precio de venta (PVP)"
            className="mt-1 block w-full min-h-[44px] rounded-ee-sm border border-ee-border-strong bg-ee-surface-elevated px-3 py-2 font-ee-mono tabular-nums text-sm text-ee-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring disabled:bg-ee-surface-subtle disabled:text-ee-text-secondary"
            value={calculator.priceInput}
            disabled={priceInputDisabled}
            onChange={(e) => calculator.onPriceChange(e.target.value)}
            onBlur={calculator.onPriceBlur}
          />
        </label>
      </div>

      {!margin.marginDerivable && (
        <p className="text-xs text-ee-text-secondary">
          No se puede calcular un margen sin Food Cost: ingresá el precio de venta directamente.
        </p>
      )}

      {discloseTolerance && (
        <p
          data-testid="tolerance-disclosure"
          className="rounded-ee-sm border border-ee-warning-border bg-ee-warning-bg px-3 py-2 text-xs text-ee-warning-fg"
        >
          A este precio, la precisión de redondeo alcanzable es de ±
          <span className="font-ee-mono tabular-nums">
            {pct.format(margin.marginTolerancePp as number)}
          </span>{' '}
          puntos porcentuales.
        </p>
      )}

      {negativeMarginPending ? (
        <div className="rounded-ee-md border border-ee-warning-border bg-ee-warning-bg p-4 space-y-3 text-ee-warning-fg">
          <div className="flex items-start gap-2">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <p className="text-sm font-medium">
              El precio ingresado es menor al costo de producción: el margen resultante es negativo.
              ¿Confirmás que esta es la decisión comercial deseada?
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="danger" size="sm" onClick={handleConfirmNegativeMargin}>
              Confirmar
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={handleAbandonNegativeMargin}>
              Cancelar
            </Button>
          </div>
        </div>
      ) : (
        <Button
          type="button"
          variant="primary"
          size="md"
          disabled={calculator.retailPrice == null}
          onClick={handleSaveClick}
        >
          Guardar
        </Button>
      )}
    </div>
  );
};

export default MarginPriceCalculator;
