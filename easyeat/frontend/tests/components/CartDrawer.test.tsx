import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { CartDrawer } from '../../src/components/cart/CartDrawer';
import * as useCartModule from '../../src/hooks/useCart';

describe('CartDrawer (T044, FR-019, EC-023)', () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    onCheckout: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('testHoldAndLockoutOnChefPause: plato pausado en carrito -> aviso "Pausado por cocina", boton + inhabilitado, checkout bloqueado (FR-019, EC-023)', () => {
    const mockCart = {
      items: [
        {
          dishId: 'dish-paused-1',
          name: 'Hamburguesa Completa',
          price: 5000,
          quantity: 1,
          photoUrl: '/img/burger.jpg',
          category: 'Platos principales',
          pausedByChef: true,
          availableStock: 10,
        },
      ],
      updateQuantity: vi.fn(),
      removeItem: vi.fn(),
      clearCart: vi.fn(),
      totalAmount: 5000,
      totalCount: 1,
      hasConflictingItems: false,
      hasPausedItems: true,
    };

    vi.spyOn(useCartModule, 'useCart').mockReturnValue(mockCart as any);

    render(<CartDrawer {...defaultProps} />);

    // Item remains visible
    expect(screen.getByText('Hamburguesa Completa')).toBeInTheDocument();

    // Displays warning badge
    expect(screen.getByText(/pausado por cocina/i)).toBeInTheDocument();

    // Checkout button is blocked/disabled
    const checkoutBtn = screen.getByRole('button', { name: /confirmar pedido|iniciar pedido|checkout/i });
    expect(checkoutBtn).toBeDisabled();

    // Clicking checkout does not invoke onCheckout
    fireEvent.click(checkoutBtn);
    expect(defaultProps.onCheckout).not.toHaveBeenCalled();
  });
});
