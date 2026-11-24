import React from 'react';
import { AlertTriangle } from 'lucide-react';

export const RESTRICTION_LABELS: Record<string, string> = {
  CELIAC: 'No apto Celíaco',
  VEGAN: 'No apto Vegano',
  VEGETARIAN: 'No apto Vegetariano',
  DAIRY: 'Contiene Lácteos',
  PEANUTS: 'Contiene Maní',
  TREE_NUTS: 'Contiene Frutos secos',
  EGGS: 'Contiene Huevos',
  FISH: 'Contiene Pescado',
  SHELLFISH: 'Contiene Mariscos',
  SOY: 'Contiene Soja',
  WHEAT: 'Contiene Trigo',
};

export interface AllergenWarningBadgeProps {
  conflicts: string[];
  size?: 'sm' | 'md';
}

export const AllergenWarningBadge: React.FC<AllergenWarningBadgeProps> = ({
  conflicts,
  size = 'md',
}) => {
  if (!conflicts || conflicts.length === 0) {
    return null;
  }

  const badgeSizeClass = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-xs';

  return (
    <div
      data-testid="allergen-warning-badge-container"
      className="flex flex-wrap gap-1.5 items-center my-1"
    >
      {conflicts.map((code) => {
        const label = RESTRICTION_LABELS[code] || `Conflicto: ${code}`;
        return (
          <span
            key={code}
            data-testid={`warning-badge-${code.toLowerCase()}`}
            className={`inline-flex items-center gap-1 font-semibold rounded-full bg-[var(--ee-danger-bg)] text-[var(--ee-danger-fg)] border border-[var(--ee-danger-border)] ${badgeSizeClass}`}
          >
            <AlertTriangle
              className="h-3.5 w-3.5 text-[var(--ee-danger-fg)] shrink-0"
              aria-hidden="true"
            />
            {label}
          </span>
        );
      })}
    </div>
  );
};

export default AllergenWarningBadge;
