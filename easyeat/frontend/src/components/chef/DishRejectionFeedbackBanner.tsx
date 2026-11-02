import React from 'react';
import { taxonomyLabel } from '@/constants/allergenTaxonomy';
import type { ChefReviewFeedbackEntry, DishProposedChanges } from '@/types/dishReview';

export interface DishRejectionFeedbackBannerProps {
  entries: ChefReviewFeedbackEntry[];
  /** Optional id→name lookup so proposed ingredient lines read as names, not identifiers. */
  ingredientNames?: Record<string, string>;
}

const dateFormat = new Intl.DateTimeFormat('es-AR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

function labelsFor(codes: string[] | null | undefined): string[] {
  return (codes ?? []).map(taxonomyLabel);
}

/**
 * The Chef's own read of an Administrator's rejection feedback (FR-023), including the proposed
 * edition the Administrator sent back (FR-042).
 *
 * Renders only what {@link ChefReviewFeedbackEntry} carries — no cost, margin or price property
 * exists on that type at all, so there is nothing here through which one could leak. The proposal
 * is culinary-only by construction too.
 */
export const DishRejectionFeedbackBanner: React.FC<DishRejectionFeedbackBannerProps> = ({
  entries,
  ingredientNames = {},
}) => {
  if (entries.length === 0) {
    return null;
  }

  return (
    <div className="space-y-2" data-testid="rejection-feedback-banner">
      {entries.map((entry) => (
        <div
          key={entry.cycleNumber}
          className="rounded border border-amber-300 bg-amber-50 p-3 text-sm"
        >
          <p className="whitespace-pre-wrap text-amber-900">{entry.comment}</p>
          <p className="mt-1 text-xs text-amber-700">
            {entry.reviewerName ?? 'Administración'} · {dateFormat.format(new Date(entry.createdAt))}
          </p>
          {entry.proposedChanges && (
            <ProposedChanges proposal={entry.proposedChanges} ingredientNames={ingredientNames} />
          )}
        </div>
      ))}
    </div>
  );
};

const ProposedChanges: React.FC<{
  proposal: DishProposedChanges;
  ingredientNames: Record<string, string>;
}> = ({ proposal, ingredientNames }) => {
  const allergens = labelsFor(proposal.allergenCodes);
  const lifestyles = labelsFor(proposal.dietaryLifestyleCodes);
  const ingredients = proposal.ingredients ?? [];

  return (
    <div
      data-testid="proposed-changes"
      className="mt-2 rounded border border-amber-200 bg-white/70 p-3 text-amber-900"
    >
      <p className="text-xs font-semibold uppercase tracking-wide text-amber-800">
        Propuesta de cambios
      </p>

      {proposal.title && (
        <p className="mt-1 text-sm">
          <span className="font-semibold">Título:</span> {proposal.title}
        </p>
      )}
      {proposal.description && (
        <p className="mt-1 text-sm">
          <span className="font-semibold">Descripción:</span> {proposal.description}
        </p>
      )}
      {allergens.length > 0 && (
        <p className="mt-1 text-sm">
          <span className="font-semibold">Alérgenos:</span> {allergens.join(', ')}
        </p>
      )}
      {lifestyles.length > 0 && (
        <p className="mt-1 text-sm">
          <span className="font-semibold">Estilos de vida:</span> {lifestyles.join(', ')}
        </p>
      )}
      {ingredients.length > 0 && (
        <div className="mt-1 text-sm">
          <span className="font-semibold">Ingredientes:</span>
          <ul className="mt-1 list-disc pl-5">
            {ingredients.map((item) => (
              <li key={item.ingredientId}>
                {ingredientNames[item.ingredientId] ?? `Ingrediente ${item.ingredientId.slice(0, 8)}`}
                {' — '}
                {item.quantity}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
};

export default DishRejectionFeedbackBanner;
