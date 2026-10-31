import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DishRejectionFeedbackBanner } from '../../../src/components/chef/DishRejectionFeedbackBanner';
import type { ChefReviewFeedbackEntry } from '../../../src/types/dishReview';

/**
 * FR-023: text, timestamp and reviewer render; no financial figure appears, which this type
 * cannot express in the first place (ChefReviewFeedbackEntry carries no cost, margin or price
 * field at all).
 */
describe('DishRejectionFeedbackBanner', () => {
  const entry: ChefReviewFeedbackEntry = {
    cycleNumber: 2,
    reviewerName: 'Lucía Ferraro',
    comment: 'El costo del salmón dispara el food cost. Usá merluza y reenviá la ficha.',
    createdAt: '2026-10-29T10:00:00Z',
  };

  it('renders nothing when there are no entries', () => {
    const { container } = render(<DishRejectionFeedbackBanner entries={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the comment, the reviewer and a formatted timestamp', () => {
    render(<DishRejectionFeedbackBanner entries={[entry]} />);

    expect(screen.getByText(entry.comment as string)).toBeInTheDocument();
    expect(screen.getByText(/Lucía Ferraro/)).toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });

  it('renders every entry when there is more than one', () => {
    const second: ChefReviewFeedbackEntry = {
      cycleNumber: 1,
      reviewerName: 'Lucía Ferraro',
      comment: 'Faltan las fotos del plato.',
      createdAt: '2026-10-20T10:00:00Z',
    };

    render(<DishRejectionFeedbackBanner entries={[entry, second]} />);

    expect(screen.getByText(entry.comment as string)).toBeInTheDocument();
    expect(screen.getByText(second.comment as string)).toBeInTheDocument();
  });
});
