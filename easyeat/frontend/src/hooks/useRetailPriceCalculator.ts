import { useCallback, useMemo, useState } from 'react';

/** FR-008: money is displayed at two decimals. Margin is also formatted to two for display. */
const DISPLAY_SCALE = 2;

export type EditedField = 'margin' | 'price' | null;

export interface UseRetailPriceCalculatorResult {
  /** The margin field's raw text, exactly as typed while it is the edited field (FR-030). */
  marginInput: string;
  /** The retail price field's raw text, exactly as typed while it is the edited field. */
  priceInput: string;
  /** Which field the Administrator is editing. The other is derived and not authoritative. */
  editedField: EditedField;
  /** The parsed margin, or `null` while `marginInput` does not hold a complete number. */
  marginPct: number | null;
  /** The parsed retail price, or `null` while `priceInput` does not hold a complete number. */
  retailPrice: number | null;
  /** False exactly when the Food Cost is 0.00 (FR-007's third boundary). */
  marginDerivable: boolean;
  /** False when the margin is absent or at/beyond 100% (FR-007's divide-by-zero boundary). */
  priceDerivable: boolean;
  error: string | null;
  onMarginChange: (raw: string) => void;
  onPriceChange: (raw: string) => void;
  onMarginBlur: () => void;
  onPriceBlur: () => void;
}

/**
 * A partial value per FR-030: empty, a lone sign, or a value ending in the decimal separator.
 * None of these are errors — they simply have no number yet.
 */
function isPartial(raw: string): boolean {
  return raw === '' || raw === '-' || raw === '.' || raw === '-.' || raw.endsWith('.');
}

function parseNumber(raw: string): number | null {
  if (isPartial(raw)) {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

/**
 * HALF_UP at the given number of decimals, correcting for the binary floating-point error that
 * would otherwise round a value like 3.33 / 0.4 = 8.325 down instead of up: in double precision
 * that quotient lands at 8.3249999999999992895, just under the midpoint. The correction is a
 * relative epsilon rather than an absolute one, because the representation error scales with the
 * magnitude of the value rather than staying fixed. This hook is a client-side preview only: the
 * authoritative figure is always computed server-side in `BigDecimal` (FR-008), so this
 * approximation never reaches storage.
 */
function roundHalfUp(value: number, decimals: number): number {
  const factor = 10 ** decimals;
  const scaled = value * factor;
  const sign = scaled < 0 ? -1 : 1;
  return (sign * Math.round(Math.abs(scaled) * (1 + 1e-9))) / factor;
}

function formatFixed(value: number): string {
  return value.toFixed(DISPLAY_SCALE);
}

/**
 * The client-side half of FR-007's bidirectional derivation, with FR-030's interaction contract:
 * the field being typed is authoritative and left exactly as typed; the other is derived and
 * reformatted to two decimals on every recomputation; a partial value clears the derived field
 * without raising an error until the Administrator leaves the field being edited.
 *
 * Spring-free counterpart of `RetailPricingCalculator` for the one thing a server round-trip
 * cannot do: react while the Administrator is still typing. It derives nothing the server will
 * not re-derive and store independently (FR-008).
 */
export function useRetailPriceCalculator(foodCost: number): UseRetailPriceCalculatorResult {
  const [marginInput, setMarginInput] = useState('');
  const [priceInput, setPriceInput] = useState('');
  const [editedField, setEditedField] = useState<EditedField>(null);

  const marginDerivable = foodCost !== 0;

  const marginPct = useMemo(() => parseNumber(marginInput), [marginInput]);
  const retailPrice = useMemo(() => parseNumber(priceInput), [priceInput]);

  const priceDerivable = marginPct !== null && marginPct < 100;

  const onMarginChange = useCallback(
    (raw: string) => {
      setEditedField('margin');
      setMarginInput(raw);

      const margin = parseNumber(raw);
      if (margin === null || !marginDerivable || margin >= 100) {
        setPriceInput('');
        return;
      }
      const price = foodCost / (1 - margin / 100);
      setPriceInput(formatFixed(roundHalfUp(price, DISPLAY_SCALE)));
    },
    [foodCost, marginDerivable]
  );

  const onPriceChange = useCallback(
    (raw: string) => {
      setEditedField('price');
      setPriceInput(raw);

      const price = parseNumber(raw);
      if (price === null || price <= 0) {
        setMarginInput('');
        return;
      }
      const margin = ((price - foodCost) / price) * 100;
      setMarginInput(formatFixed(roundHalfUp(margin, DISPLAY_SCALE)));
    },
    [foodCost]
  );

  const onMarginBlur = useCallback(() => {
    const margin = parseNumber(marginInput);
    if (margin !== null) {
      setMarginInput(formatFixed(roundHalfUp(margin, DISPLAY_SCALE)));
    }
    // FR-030: the derived field is "not editable until editing ends" — editing has now ended,
    // so neither field is authoritative over the other until the Administrator types again.
    setEditedField(null);
  }, [marginInput]);

  const onPriceBlur = useCallback(() => {
    const price = parseNumber(priceInput);
    if (price !== null) {
      setPriceInput(formatFixed(roundHalfUp(price, DISPLAY_SCALE)));
    }
    setEditedField(null);
  }, [priceInput]);

  return {
    marginInput,
    priceInput,
    editedField,
    marginPct,
    retailPrice,
    marginDerivable,
    priceDerivable,
    error: null,
    onMarginChange,
    onPriceChange,
    onMarginBlur,
    onPriceBlur,
  };
}
