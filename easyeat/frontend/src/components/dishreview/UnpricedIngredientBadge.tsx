import React from 'react';
import { AlertTriangle } from 'lucide-react';
import { StatusBadge } from '@/components/primitives';

export interface UnpricedIngredientBadgeProps {
  className?: string;
}

/**
 * Marks a recipe line whose ingredient carries no acquisition cost (FR-006).
 */
export const UnpricedIngredientBadge: React.FC<UnpricedIngredientBadgeProps> = ({
  className = '',
}) => (
  <span
    data-testid="unpriced-ingredient-badge"
    title="Este insumo no tiene costo de adquisición cargado, así que no suma al Food Cost."
    className={`inline-flex items-center ${className}`}
  >
    <StatusBadge variant="warning" className="gap-1">
      <AlertTriangle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>Sin costo</span>
    </StatusBadge>
  </span>
);

export default UnpricedIngredientBadge;
