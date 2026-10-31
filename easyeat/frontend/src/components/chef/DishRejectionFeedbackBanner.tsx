import React from 'react';
import type { ChefReviewFeedbackEntry } from '@/types/dishReview';

export interface DishRejectionFeedbackBannerProps {
  entries: ChefReviewFeedbackEntry[];
}

const dateFormat = new Intl.DateTimeFormat('es-AR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

/**
 * The Chef's own read of an Administrator's rejection feedback (FR-023). Renders only what
 * {@link ChefReviewFeedbackEntry} carries — no cost, margin or price property exists on that
 * type at all, so there is nothing here through which one could leak.
 */
export const DishRejectionFeedbackBanner: React.FC<DishRejectionFeedbackBannerProps> = ({
  entries,
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
        </div>
      ))}
    </div>
  );
};

export default DishRejectionFeedbackBanner;
