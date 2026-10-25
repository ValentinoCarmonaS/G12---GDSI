import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DishCard } from '../../src/components/catalog/DishCard';
import { EnrichedDish } from '../../src/types/catalog';

describe('DishCard Component (T057, US3, FR-020)', () => {
  const compliantDish: EnrichedDish = {
    id: 'dish-1',
    tenantId: 'tenant-1',
    name: 'Ensalada Fresca',
    description: 'Tomate, lechuga y zanahoria',
    price: 4500,
    isActive: true,
    isCompliant: true,
    conflictingRestrictions: [],
  };

  const conflictingDish: EnrichedDish = {
    id: 'dish-2',
    tenantId: 'tenant-1',
    name: 'Tarta de Nueces',
    description: 'Nueces y harina de trigo',
    price: 6000,
    isActive: true,
    isCompliant: false,
    conflictingRestrictions: ['TREE_NUTS', 'WHEAT'],
  };

  it('renders compliant dish without warning badges', () => {
    const handleAddToCart = vi.fn();
    render(<DishCard dish={compliantDish} onAddToCart={handleAddToCart} />);

    expect(screen.getByText('Ensalada Fresca')).toBeInTheDocument();
    expect(screen.getByText('Tomate, lechuga y zanahoria')).toBeInTheDocument();
    expect(screen.getByText('✓ Apto según tu perfil')).toBeInTheDocument();
    expect(screen.queryByTestId('allergen-warning-badge-container')).not.toBeInTheDocument();

    const addBtn = screen.getByTestId(`add-to-cart-btn-${compliantDish.id}`);
    fireEvent.click(addBtn);
    expect(handleAddToCart).toHaveBeenCalledWith(compliantDish);
  });

  it('renders conflicting dish with prominent inline allergen warning badges (FR-020)', () => {
    const handleAddToCart = vi.fn();
    render(<DishCard dish={conflictingDish} onAddToCart={handleAddToCart} />);

    expect(screen.getByText('Tarta de Nueces')).toBeInTheDocument();
    expect(screen.getByText(/Contiene Frutos secos/i)).toBeInTheDocument();
    expect(screen.getByText(/Contiene Trigo/i)).toBeInTheDocument();
    expect(screen.getByText('⚠️ Requiere confirmación')).toBeInTheDocument();

    const addBtn = screen.getByTestId(`add-to-cart-btn-${conflictingDish.id}`);
    fireEvent.click(addBtn);
    expect(handleAddToCart).toHaveBeenCalledWith(conflictingDish);
  });
});
