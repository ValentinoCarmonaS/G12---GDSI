/**
 * Currency utilities enforcing fixed-point integer cent arithmetic (INV-2).
 * Eliminates IEEE-754 floating-point drift across multi-item multiplications and summations.
 */

/**
 * Converts a decimal monetary amount to integer cents with exact half-up rounding.
 * e.g., 19.99 -> 1999, 10.5 -> 1050
 */
export function toCents(amount: number): number {
  if (typeof amount !== 'number' || isNaN(amount)) return 0;
  return Math.round(amount * 100);
}

/**
 * Converts integer cents back to a decimal number.
 * e.g., 1999 -> 19.99, 1050 -> 10.5
 */
export function fromCents(cents: number): number {
  if (typeof cents !== 'number' || isNaN(cents)) return 0;
  return Math.round(cents) / 100;
}

/**
 * Calculates a line item subtotal in integer cents, avoiding float multiplication drift.
 * e.g., unitPrice: 19.99, quantity: 3 -> 5997 cents
 */
export function calculateLineTotalCents(unitPrice: number, quantity: number): number {
  const priceCents = toCents(unitPrice);
  const safeQty = Math.max(0, Math.floor(quantity || 0));
  return priceCents * safeQty;
}

/**
 * Calculates a line item subtotal in standard decimal currency.
 */
export function calculateLineTotal(unitPrice: number, quantity: number): number {
  return fromCents(calculateLineTotalCents(unitPrice, quantity));
}

/**
 * Calculates consolidated grand total in integer cents across multiple line items.
 */
export function calculateCartTotalCents(items: Array<{ price: number; quantity: number }>): number {
  if (!items || !Array.isArray(items)) return 0;
  return items.reduce((sum, item) => sum + calculateLineTotalCents(item.price, item.quantity), 0);
}

/**
 * Calculates consolidated grand total in standard decimal currency with exact precision.
 */
export function calculateCartTotal(items: Array<{ price: number; quantity: number }>): number {
  return fromCents(calculateCartTotalCents(items));
}

/**
 * Formats a monetary amount for user display in Argentine Pesos (es-AR locale).
 * Guarantees exactly 2 decimal digits and currency prefix.
 */
export function formatCurrency(
  amountOrCents: number,
  options: { isCents?: boolean } = {}
): string {
  const decimalAmount = options.isCents ? fromCents(amountOrCents) : amountOrCents;
  const safeValue = isNaN(decimalAmount) ? 0 : decimalAmount;
  return `$${safeValue.toLocaleString('es-AR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}
