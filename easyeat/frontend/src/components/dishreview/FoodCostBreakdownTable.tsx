import React from 'react';
import { formatAuditQuantity } from '@/utils/foodCostAudit';
import { UnpricedIngredientBadge } from './UnpricedIngredientBadge';
import { GlassCard } from '@/components/admin/GlassCard';
import type { FoodCostAudit } from '@/types/dishReview';

export interface FoodCostBreakdownTableProps {
  audit: FoodCostAudit;
}

const money = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/**
 * Itemized Food Cost audit of FR-006 rendered in responsive GlassCard rows.
 * Figures come verbatim from spec 006 without client-side recomputation.
 */
export const FoodCostBreakdownTable: React.FC<FoodCostBreakdownTableProps> = ({ audit }) => (
  <div className="space-y-3" aria-label="Desglose del Food Cost por insumo">
    <ul className="space-y-2">
      {audit.items.map((line) => (
        <li key={line.ingredientId}>
          <GlassCard
            data-testid="cost-line"
            variant="subtle"
            padding="sm"
            radius="md"
            className={`px-4 py-3 ${
              line.unpricedWarning ? 'border-ee-warning-border bg-ee-warning-bg/40' : ''
            }`}
          >
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-semibold text-ee-text">{line.ingredientName}</span>
                  {line.unpricedWarning && <UnpricedIngredientBadge />}
                </div>
                <p className="mt-1 font-ee-mono tabular-nums text-xs text-ee-text-secondary">
                  {formatAuditQuantity(line)} {line.unitOfMeasure ?? '—'} · Costo unit.: $
                  {money.format(line.unitPurchaseCost)}
                </p>
              </div>
              <div className="shrink-0 text-right">
                <span className="block font-ee-mono text-[11px] uppercase tracking-wider text-ee-text-secondary">
                  Subtotal
                </span>
                <span className="font-ee-mono tabular-nums text-sm font-bold text-ee-text">
                  ${money.format(line.lineSubtotal)}
                </span>
              </div>
            </div>
          </GlassCard>
        </li>
      ))}
    </ul>

    <GlassCard
      variant="accent"
      padding="sm"
      radius="md"
      className="flex items-center justify-between gap-4 px-4 py-3"
    >
      <span className="text-sm font-semibold text-ee-text">Food Cost por porción</span>
      <span
        data-testid="food-cost-total"
        className="font-ee-mono tabular-nums text-lg font-bold text-ee-primary"
      >
        ${money.format(audit.totalFoodCost)}
      </span>
    </GlassCard>
  </div>
);

export default FoodCostBreakdownTable;
