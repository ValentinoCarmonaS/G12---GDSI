import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { FloatingCartButton } from '../../src/components/cart/FloatingCartButton';
import { CartContext, CartContextType } from '../../src/context/CartContext';

describe('FloatingCartButton Component (T025, US2, COM-6.1.2, INV-6, INV-7, SC-002, SC-009)', () => {
  const defaultCartContext: CartContextType = {
    items: [],
    addItem: vi.fn(),
    removeItem: vi.fn(),
    updateQuantity: vi.fn(),
    clearCart: vi.fn(),
    reEvaluateCompliance: vi.fn(),
    totalAmount: 0,
    totalCount: 0,
    hasConflictingItems: false,
  };

  const renderWithCart = (cartValues: Partial<CartContextType> = {}, onClick = vi.fn()) => {
    const merged = { ...defaultCartContext, ...cartValues };
    return {
      onClick,
      ...render(
        <CartContext.Provider value={merged}>
          <FloatingCartButton onClick={onClick} />
        </CartContext.Provider>
      ),
    };
  };

  it('hides completely (renders null) when cart has 0 items (INV-6, FR-008, SC-009)', () => {
    renderWithCart({ totalCount: 0, items: [], totalAmount: 0 });

    expect(screen.queryByTestId('floating-cart-button')).not.toBeInTheDocument();
    expect(screen.queryByTestId('open-cart-button')).not.toBeInTheDocument();
  });

  it('renders fixed bottom-right button when totalCount >= 1 (INV-7, FR-005, FR-006)', () => {
    renderWithCart({
      totalCount: 3,
      totalAmount: 59.97,
      items: [
        {
          dishId: 'dish-1',
          name: 'Milanesa',
          price: 19.99,
          quantity: 3,
          isCompliant: true,
        },
      ],
    });

    const button = screen.getByTestId('floating-cart-button');
    expect(button).toBeInTheDocument();
    expect(button.className).toContain('fixed');
    expect(button.className).toMatch(/bottom-\d+/);
    expect(button.className).toMatch(/right-\d+/);

    // Displays totalCount badge and exact formatted total
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText(/\$\s?59,97|\$59\.97/)).toBeInTheDocument();
  });

  it('dispatches onClick when clicked (FR-009)', () => {
    const mockClick = vi.fn();
    renderWithCart(
      {
        totalCount: 1,
        totalAmount: 2500,
        items: [
          {
            dishId: 'dish-1',
            name: 'Plato',
            price: 2500,
            quantity: 1,
          },
        ],
      },
      mockClick
    );

    const button = screen.getByTestId('floating-cart-button');
    fireEvent.click(button);

    expect(mockClick).toHaveBeenCalledTimes(1);
  });

  it('provides accessible role and aria labels', () => {
    renderWithCart({
      totalCount: 2,
      totalAmount: 3000,
      items: [{ dishId: 'dish-1', name: 'Plato', price: 1500, quantity: 2 }],
    });

    const button = screen.getByRole('button', { name: /ver carrito|abrir carrito/i });
    expect(button).toBeInTheDocument();
  });
});
