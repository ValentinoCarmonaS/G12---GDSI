import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DishDetailModal } from '@/components/catalog/DishDetailModal';
import type { DishCatalogItem } from '@/types/dinerCatalog';
import { CartContext, CartContextType } from '@/context/CartContext';

const mockDishWithStock: DishCatalogItem & { availableStock?: number } = {
  id: 'dish-detail-1',
  name: 'Lomo al Malbec',
  description: 'Lomo con reducción de vino y papas rústicas.',
  category: 'Platos principales',
  price: 8500.0,
  photoUrl: '/api/v1/dishes/dish-detail-1/photo',
  isAvailable: true,
  isCompliant: true,
  conflictingRestrictions: [],
  recipeIngredients: ['Lomo', 'Vino Malbec', 'Papas'],
  availableStock: 5,
};

const mockOutOfStockDish: DishCatalogItem & { availableStock?: number } = {
  id: 'dish-detail-2',
  name: 'Volcán de Chocolate',
  description: 'Volcán tibio con corazón de chocolate fundido.',
  category: 'Postres',
  price: 3200.0,
  photoUrl: null,
  isAvailable: false,
  isCompliant: true,
  conflictingRestrictions: [],
  recipeIngredients: ['Chocolate', 'Manteca', 'Huevos'],
  availableStock: 0,
};

describe('DishDetailModal Component Tests - User Story 1 (T020, COM-6.1.1, FR-001, FR-003, INV-3)', () => {
  it('renders numeric quantity selector defaulting to 1 when dish has available stock', () => {
    render(<DishDetailModal dish={mockDishWithStock} isOpen={true} onClose={vi.fn()} />);

    expect(screen.getByTestId('quantity-selector')).toBeInTheDocument();
    expect(screen.getByTestId('quantity-value')).toHaveTextContent('1');
    expect(screen.getByTestId('add-to-cart-btn')).toBeInTheDocument();
    expect(screen.getByTestId('add-to-cart-btn')).not.toBeDisabled();
  });

  it('increments and decrements quantity within bounds [1, availableStock]', () => {
    render(<DishDetailModal dish={mockDishWithStock} isOpen={true} onClose={vi.fn()} />);

    const incBtn = screen.getByTestId('quantity-increment-btn');
    const decBtn = screen.getByTestId('quantity-decrement-btn');
    const valueDisplay = screen.getByTestId('quantity-value');

    // Default 1 -> dec disabled
    expect(decBtn).toBeDisabled();

    // Increment to 2
    fireEvent.click(incBtn);
    expect(valueDisplay).toHaveTextContent('2');
    expect(decBtn).not.toBeDisabled();

    // Increment up to availableStock (5)
    fireEvent.click(incBtn); // 3
    fireEvent.click(incBtn); // 4
    fireEvent.click(incBtn); // 5
    expect(valueDisplay).toHaveTextContent('5');

    // At limit (5), increment must be disabled
    expect(incBtn).toBeDisabled();
    expect(screen.getByTestId('stock-limit-warning')).toBeInTheDocument();

    // Additional click does not exceed 5
    fireEvent.click(incBtn);
    expect(valueDisplay).toHaveTextContent('5');

    // Decrement back to 4
    fireEvent.click(decBtn);
    expect(valueDisplay).toHaveTextContent('4');
    expect(incBtn).not.toBeDisabled();
  });

  it('disables Add to Cart button and selector when dish is out of stock (INV-3)', () => {
    render(<DishDetailModal dish={mockOutOfStockDish} isOpen={true} onClose={vi.fn()} />);

    expect(screen.getByTestId('add-to-cart-btn')).toBeDisabled();
    expect(screen.getByText(/Agotado/i)).toBeInTheDocument();
  });

  it('disables Add to Cart button when catalog is in read-only mode (outside operating hours)', () => {
    render(
      <DishDetailModal
        dish={mockDishWithStock}
        isOpen={true}
        onClose={vi.fn()}
        isReadOnly={true}
      />
    );

    expect(screen.getByTestId('add-to-cart-btn')).toBeDisabled();
  });

  it('dispatches onAddToCart callback with selected quantity when Add to Cart is clicked', () => {
    const onAddToCart = vi.fn();
    const onClose = vi.fn();

    render(
      <DishDetailModal
        dish={mockDishWithStock}
        isOpen={true}
        onClose={onClose}
        onAddToCart={onAddToCart}
      />
    );

    const incBtn = screen.getByTestId('quantity-increment-btn');
    fireEvent.click(incBtn); // 2
    fireEvent.click(incBtn); // 3

    const addBtn = screen.getByTestId('add-to-cart-btn');
    fireEvent.click(addBtn);

    expect(onAddToCart).toHaveBeenCalledTimes(1);
    expect(onAddToCart).toHaveBeenCalledWith(mockDishWithStock, 3);
  });

  it('accounts for quantity already present in CartContext when calculating remaining stock limit', () => {
    const mockCartContext: CartContextType = {
      items: [
        {
          dishId: mockDishWithStock.id,
          name: mockDishWithStock.name,
          price: mockDishWithStock.price,
          quantity: 4, // 4 already in cart, stock is 5 -> only 1 unit remaining
        },
      ],
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
      reEvaluateCompliance: vi.fn(),
      totalAmount: mockDishWithStock.price * 4,
      totalCount: 4,
      hasConflictingItems: false,
    };

    render(
      <CartContext.Provider value={mockCartContext}>
        <DishDetailModal dish={mockDishWithStock} isOpen={true} onClose={vi.fn()} />
      </CartContext.Provider>
    );

    // Initial quantity is 1 (the only remaining unit)
    expect(screen.getByTestId('quantity-value')).toHaveTextContent('1');

    // Increment is immediately disabled because 1 + 4 = 5 = availableStock
    const incBtn = screen.getByTestId('quantity-increment-btn');
    expect(incBtn).toBeDisabled();
    expect(screen.getByTestId('stock-limit-warning')).toBeInTheDocument();
  });

  it('disables Add to Cart completely if existing cart quantity equals or exceeds availableStock', () => {
    const mockCartContext: CartContextType = {
      items: [
        {
          dishId: mockDishWithStock.id,
          name: mockDishWithStock.name,
          price: mockDishWithStock.price,
          quantity: 5, // 5 already in cart, stock is 5 -> 0 remaining
        },
      ],
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
      reEvaluateCompliance: vi.fn(),
      totalAmount: mockDishWithStock.price * 5,
      totalCount: 5,
      hasConflictingItems: false,
    };

    render(
      <CartContext.Provider value={mockCartContext}>
        <DishDetailModal dish={mockDishWithStock} isOpen={true} onClose={vi.fn()} />
      </CartContext.Provider>
    );

    expect(screen.getByTestId('add-to-cart-btn')).toBeDisabled();
    expect(screen.getByTestId('stock-limit-warning')).toHaveTextContent(/Límite de disponibilidad alcanzado/i);
  });
});
