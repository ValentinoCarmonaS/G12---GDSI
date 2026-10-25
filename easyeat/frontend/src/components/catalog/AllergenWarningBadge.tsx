import React from 'react';

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
            className={`inline-flex items-center font-medium rounded-full bg-red-100 text-red-800 border border-red-200 ${badgeSizeClass}`}
          >
            <svg
              className="mr-1 h-3.5 w-3.5 text-red-600 flex-shrink-0"
              fill="currentColor"
              viewBox="0 0 20 20"
              aria-hidden="true"
            >
              <path
                fillRule="evenodd"
                d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                clipRule="evenodd"
              />
            </svg>
            {label}
          </span>
        );
      })}
    </div>
  );
};

export default AllergenWarningBadge;
