import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { DishStatusBadge } from '../../../src/components/dish/DishStatusBadge';

/**
 * T079a: before this, 'PUBLISHED' had no entry in STATUS_CONFIGS, so the lookup missed and the
 * fallback config used `label: status`, rendering the raw English token in the middle of a
 * Spanish interface — and with the generic grey fallback styling, saying nothing about the state.
 * This asserts the token is gone and a translated label with its own styling takes its place.
 */
describe('DishStatusBadge', () => {
  it.each([
    ['DRAFT', 'Borrador'],
    ['PENDING_PRICE', 'Pendiente de Precio'],
  ])('renders the existing %s label unchanged', (status, label) => {
    render(<DishStatusBadge status={status} />);
    expect(screen.getByTestId('dish-status-badge')).toHaveTextContent(label);
  });

  it('renders a translated label for PUBLISHED, not the raw token', () => {
    render(<DishStatusBadge status="PUBLISHED" />);

    const badge = screen.getByTestId('dish-status-badge');
    expect(badge).not.toHaveTextContent('PUBLISHED');
    expect(badge).toHaveTextContent('Publicado');
  });

  it('gives PUBLISHED its own styling rather than the neutral fallback', () => {
    // render()'s returned queries search the whole document, same as `screen`, so each render
    // is scoped through its own `container` to tell the two badges apart.
    const published = render(<DishStatusBadge status="PUBLISHED" />);
    const badge = within(published.container).getByTestId('dish-status-badge');

    const fallback = render(<DishStatusBadge status="some-unknown-status" />);
    const fallbackBadge = within(fallback.container).getByTestId('dish-status-badge');

    expect(badge.className).not.toBe(fallbackBadge.className);
  });
});
