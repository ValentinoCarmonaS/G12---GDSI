import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { DishDetailModal } from '../../src/components/catalog/DishDetailModal';
import type { DishCatalogItem } from '@/types/dinerCatalog';

describe('DishDetailModal (T043, EC-017, FR-016, INV-15)', () => {
  const baseDish: DishCatalogItem & { availableStock?: number; pausedByChef?: boolean } = {
    id: 'dish-101',
    name: 'Ravioles de Ricota',
    description: 'Con salsa bolognesa',
    price: 4500,
    photoUrl: '/img/ravioles.jpg',
    category: 'Platos principales',
    allergens: [],
    dietaryRestrictions: [],
    availableStock: 5,
    isAvailable: true,
    pausedByChef: false,
  };

  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    onAddToCart: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('testReactivePauseWithoutClosing: pausa llega al modal abierto -> deshabilita compra sin cerrar panel (EC-017, FR-016, INV-15)', () => {
    const { rerender } = render(
      <DishDetailModal {...defaultProps} dish={baseDish} />
    );

    // Initial state: available to add to cart
    expect(screen.getByRole('heading', { name: /ravioles de ricota/i })).toBeInTheDocument();
    const addButton = screen.getByTestId('add-to-cart-btn');
    expect(addButton).not.toBeDisabled();

    // Dish updated reactively to pausedByChef = true
    const pausedDish = { ...baseDish, pausedByChef: true, isAvailable: false };
    rerender(<DishDetailModal {...defaultProps} dish={pausedDish} />);

    // Panel does NOT close
    expect(screen.getByRole('heading', { name: /ravioles de ricota/i })).toBeInTheDocument();
    expect(defaultProps.onClose).not.toHaveBeenCalled();

    // Shows "Pausado por cocina" warning badge and disables purchase button
    expect(screen.getByText(/pausado por cocina/i)).toBeInTheDocument();
    expect(screen.getByTestId('add-to-cart-btn')).toBeDisabled();
  });
});
