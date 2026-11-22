import React from 'react';
import { ShieldAlert } from 'lucide-react';

export interface AllergenNeutralPillProps {
  allergen: string;
}

/**
 * AllergenNeutralPill Component (Spec 011 + Spec 025 FR-007 / FR-008)
 * Renders neutral, objective, and uniform allergen labels using Bento WCAG 2.2 AA
 * neutral tokens (strictly isolated from restaurant `--brand-*` slots).
 */
export const AllergenNeutralPill: React.FC<AllergenNeutralPillProps> = ({ allergen }) => {
  return (
    <span
      data-testid="allergen-neutral-pill"
      className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[var(--ee-neutral-bg)] text-[var(--ee-neutral-fg)] border border-[var(--ee-neutral-border)]"
    >
      <ShieldAlert className="w-3 h-3 shrink-0" aria-hidden="true" />
      <span>{allergen}</span>
    </span>
  );
};
