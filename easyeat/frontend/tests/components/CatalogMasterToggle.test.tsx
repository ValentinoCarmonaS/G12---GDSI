import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CatalogMasterToggle } from '../../src/components/catalog/CatalogMasterToggle';

describe('CatalogMasterToggle Component (T043, US3, FR-019, FR-025)', () => {
  it('hides the master toggle completely when activeRestrictionsCount is 0 (FR-025)', () => {
    const handleToggle = vi.fn();
    const { container } = render(
      <CatalogMasterToggle
        activeRestrictionsCount={0}
        isFilterActive={true}
        onToggle={handleToggle}
      />
    );

    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId('catalog-master-toggle-container')).not.toBeInTheDocument();
  });

  it('renders visible master toggle when activeRestrictionsCount > 0 with default ON state (FR-019)', () => {
    const handleToggle = vi.fn();
    render(
      <CatalogMasterToggle
        activeRestrictionsCount={2}
        isFilterActive={true}
        onToggle={handleToggle}
      />
    );

    expect(screen.getByTestId('catalog-master-toggle-container')).toBeInTheDocument();
    expect(
      screen.getByText(/Filtrar por mis restricciones de perfil \(2\)/i)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Mostrando únicamente platos aptos para tu dieta/i)
    ).toBeInTheDocument();

    const switchBtn = screen.getByRole('switch');
    expect(switchBtn).toHaveAttribute('aria-checked', 'true');
  });

  it('calls onToggle with false when clicked while active (flipping toggle OFF)', () => {
    const handleToggle = vi.fn();
    render(
      <CatalogMasterToggle
        activeRestrictionsCount={1}
        isFilterActive={true}
        onToggle={handleToggle}
      />
    );

    const switchBtn = screen.getByRole('switch');
    fireEvent.click(switchBtn);

    expect(handleToggle).toHaveBeenCalledTimes(1);
    expect(handleToggle).toHaveBeenCalledWith(false);
  });

  it('displays warning caption when isFilterActive is false (FR-020)', () => {
    const handleToggle = vi.fn();
    render(
      <CatalogMasterToggle
        activeRestrictionsCount={1}
        isFilterActive={false}
        onToggle={handleToggle}
      />
    );

    expect(
      screen.getByText(/Mostrando menú completo con advertencias visuales/i)
    ).toBeInTheDocument();

    const switchBtn = screen.getByRole('switch');
    expect(switchBtn).toHaveAttribute('aria-checked', 'false');

    fireEvent.click(switchBtn);
    expect(handleToggle).toHaveBeenCalledWith(true);
  });
});
