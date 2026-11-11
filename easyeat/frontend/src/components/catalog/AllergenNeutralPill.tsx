import React from 'react';

export interface AllergenNeutralPillProps {
  allergen: string;
}

/**
 * AllergenNeutralPill Component (T029, US3, COM-5.1.2, INV-6, Clarification #4)
 * Renders neutral, objective, and uniform allergen labels without personalized
 * dietary warning badges or profile-based alerts in the dish detail view.
 */
export const AllergenNeutralPill: React.FC<AllergenNeutralPillProps> = ({ allergen }) => {
  return (
    <span
      data-testid="allergen-neutral-pill"
      style={{
        backgroundColor: 'var(--brand-accent, #F59E0B)',
        color: 'var(--brand-accent-contrast, #000000)',
      }}
      className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold shadow-xs transition-colors"
    >
      {allergen}
    </span>
  );
};
