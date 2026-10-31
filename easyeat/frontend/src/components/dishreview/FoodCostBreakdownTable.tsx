import React from 'react';
import { UnpricedIngredientBadge } from './UnpricedIngredientBadge';
import type { FoodCostAudit } from '@/types/dishReview';

export interface FoodCostBreakdownTableProps {
  audit: FoodCostAudit;
}

const money = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const quantity = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 4,
});

/**
 * The itemized Food Cost audit of FR-006: one line per recipe entry, with the aggregate per
 * portion.
 *
 * Every figure comes from spec 006 and none is recomputed here. The totals in particular are
 * displayed, not summed in the browser: spec 006 rounds each line subtotal to two decimals and
 * sums the rounded values, so a client-side sum of the displayed lines could legitimately differ
 * from the authoritative total by a fraction of a cent and would look like a bug in one of them.
 */
export const FoodCostBreakdownTable: React.FC<FoodCostBreakdownTableProps> = ({ audit }) => (
  <div className="overflow-x-auto">
    <table className="min-w-full text-sm">
      <caption className="sr-only">Desglose del Food Cost por insumo</caption>
      <thead>
        <tr className="text-left text-xs uppercase tracking-wide text-gray-500 border-b border-gray-200">
          <th scope="col" className="py-2 pr-4">Insumo</th>
          <th scope="col" className="py-2 pr-4">Cantidad</th>
          <th scope="col" className="py-2 pr-4">Unidad</th>
          <th scope="col" className="py-2 pr-4 text-right">Costo unitario</th>
          <th scope="col" className="py-2 text-right">Subtotal</th>
        </tr>
      </thead>
      <tbody>
        {audit.items.map((line) => (
          <tr key={line.ingredientId} data-testid="cost-line" className="border-b border-gray-100">
            <td className="py-2 pr-4">
              <span className="text-gray-900">{line.ingredientName}</span>
              {line.unpricedWarning && <UnpricedIngredientBadge className="ml-2" />}
            </td>
            <td className="py-2 pr-4 text-gray-700">{quantity.format(line.quantity)}</td>
            <td className="py-2 pr-4 text-gray-500">{line.unitOfMeasure ?? '—'}</td>
            <td className="py-2 pr-4 text-right text-gray-700">
              ${money.format(line.unitPurchaseCost)}
            </td>
            <td className="py-2 text-right text-gray-900">${money.format(line.lineSubtotal)}</td>
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr>
          <th scope="row" colSpan={4} className="py-3 pr-4 text-right font-medium text-gray-700">
            Food Cost por porción
          </th>
          {/*
            Rendered as a figure even at zero. 0,00 is a real cost - a recipe whose every
            ingredient is unpriced - and a dash would make it indistinguishable from "not
            computed", which is a different thing to tell someone pricing a dish.
          */}
          <td data-testid="food-cost-total" className="py-3 text-right font-semibold text-gray-900">
            ${money.format(audit.totalFoodCost)}
          </td>
        </tr>
      </tfoot>
    </table>
  </div>
);

export default FoodCostBreakdownTable;
