import React from 'react';

export interface UnpricedIngredientBadgeProps {
  className?: string;
}

/**
 * Marks a recipe line whose ingredient carries no acquisition cost (FR-006).
 *
 * Amber rather than red on purpose: the line is not an error, it is a gap in the inventory data
 * that makes the Food Cost understated. The dish remains publishable once the Administrator
 * acknowledges it (FR-017), so styling it as a failure would overstate the problem.
 */
export const UnpricedIngredientBadge: React.FC<UnpricedIngredientBadgeProps> = ({
  className = '',
}) => (
  <span
    data-testid="unpriced-ingredient-badge"
    title="Este insumo no tiene costo de adquisición cargado, así que no suma al Food Cost."
    className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-800 border border-amber-300 ${className}`}
  >
    <span aria-hidden="true">⚠</span>
    Sin costo
  </span>
);

export default UnpricedIngredientBadge;
