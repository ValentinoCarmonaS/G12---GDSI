import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { EmptyCatalogDietaryState } from '../../src/components/catalog/EmptyCatalogDietaryState';

describe('EmptyCatalogDietaryState Component (T058, US3, FR-024, SC-006)', () => {
  it('renders informative message when zero compliant dishes exist', () => {
    const handleViewFullMenu = vi.fn();
    render(<EmptyCatalogDietaryState onViewFullMenu={handleViewFullMenu} />);

    expect(screen.getByTestId('empty-catalog-dietary-state')).toBeInTheDocument();
    expect(
      screen.getByText(/No encontramos platos que coincidan con tu dieta/i)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Este local no dispone de platos que satisfagan simultáneamente/i)
    ).toBeInTheDocument();
  });

  it('triggers onViewFullMenu when user clicks action button (FR-024, SC-006)', () => {
    const handleViewFullMenu = vi.fn();
    render(<EmptyCatalogDietaryState onViewFullMenu={handleViewFullMenu} />);

    const actionBtn = screen.getByTestId('view-full-menu-button');
    expect(actionBtn).toBeInTheDocument();

    fireEvent.click(actionBtn);
    expect(handleViewFullMenu).toHaveBeenCalledTimes(1);
  });
});
