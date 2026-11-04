/**
 * Shared reading and formatting of the Food Cost audit's recipe quantity.
 *
 * The canonical key is `recipeQuantity` (spec 006's `RecipeItemAuditResponse`). It used to be read
 * as `quantity` in the Admin review table, which types the field but does not exist in the JSON the
 * backend sends: every line silently rendered as `0`. This helper reads the canonical key first and
 * only then falls back to plausible aliases, so a future rename degrades to a number rather than to
 * a table full of zeroes.
 */
export interface AuditQuantityLike {
  recipeQuantity?: number | string | null;
  quantity?: number | string | null;
  amount?: number | string | null;
  quantityKg?: number | string | null;
}

/** es-AR: keeps decimals when they exist (`0,4`) and never pads with trailing zeroes. */
export const AUDIT_QUANTITY_FORMAT = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 4,
});

/** The numeric recipe quantity of an audit line, defensively read. Never returns NaN. */
export function readAuditQuantity(item: AuditQuantityLike | null | undefined): number {
  if (!item) return 0;

  const raw =
    item.recipeQuantity ?? item.quantity ?? item.amount ?? item.quantityKg ?? 0;
  const numeric = typeof raw === 'number' ? raw : Number(raw);

  return Number.isFinite(numeric) ? numeric : 0;
}

/** The audit quantity as a display string, preserving meaningful decimals. */
export function formatAuditQuantity(item: AuditQuantityLike | null | undefined): string {
  return AUDIT_QUANTITY_FORMAT.format(readAuditQuantity(item));
}
