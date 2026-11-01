import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CartDrawer } from '../../src/components/cart/CartDrawer';
import { CartProvider } from '../../src/context/CartContext';
import { CartItem } from '../../src/types/cart';

describe('CartDrawer Component', () => {
  const mockOnClose = vi.fn();
  const mockOnCheckout = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  const compliantItem: CartItem = {
    dishId: 'dish-1',
    name: 'Ensalada César Vegana',
    price: 1500,
    quantity: 2,
    isCompliant: true,
    conflictingRestrictions: [],
    riskConfirmed: false,
  };

  const conflictingItem: CartItem = {
    dishId: 'dish-2',
    name: 'Pizza Napolitana',
    price: 2500,
    quantity: 1,
    isCompliant: false,
    conflictingRestrictions: ['DAIRY', 'WHEAT'],
    riskConfirmed: true,
  };

  const renderWithCart = (initialItems: CartItem[] = [], isOpen = true) => {
    if (initialItems.length > 0) {
      sessionStorage.setItem('easyeat_cart', JSON.stringify(initialItems));
    }
    return render(
      <CartProvider>
        <CartDrawer isOpen={isOpen} onClose={mockOnClose} onCheckout={mockOnCheckout} />
      </CartProvider>
    );
  };

  it('does not render anything when isOpen is false', () => {
    renderWithCart([], false);
    expect(screen.queryByTestId('cart-drawer')).not.toBeInTheDocument();
  });

  it('renders empty cart state when open with no items', () => {
    renderWithCart([], true);
    expect(screen.getByTestId('cart-drawer')).toBeInTheDocument();
    expect(screen.getByTestId('cart-empty-message')).toBeInTheDocument();
    expect(screen.getByText('Tu carrito está vacío')).toBeInTheDocument();
    expect(screen.queryByTestId('cart-conflict-banner')).not.toBeInTheDocument();
    expect(screen.queryByTestId('cart-checkout-button')).not.toBeInTheDocument();
  });

  it('renders items, calculates total and does not show conflict banner for compliant items', () => {
    renderWithCart([compliantItem], true);

    expect(screen.getByText('Ensalada César Vegana')).toBeInTheDocument();
    expect(screen.getByTestId('cart-total-amount')).toHaveTextContent(/3\.000/);
    expect(screen.queryByTestId('cart-conflict-banner')).not.toBeInTheDocument();
  });

  it('displays persistent conflict banner and allergen warning badge when conflicting items are present (FR-022)', () => {
    renderWithCart([compliantItem, conflictingItem], true);

    expect(screen.getByTestId('cart-conflict-banner')).toBeInTheDocument();
    expect(screen.getByText(/Tu carrito contiene platos que no cumplen con tu perfil dietario/i)).toBeInTheDocument();

    expect(screen.getByText('Pizza Napolitana')).toBeInTheDocument();
    expect(screen.getByText(/Contiene Lácteos/i)).toBeInTheDocument();
    expect(screen.getByText(/Contiene Trigo/i)).toBeInTheDocument();
  });

  it('handles item quantity increment, decrement, and removal', () => {
    renderWithCart([compliantItem], true);

    const incrementBtn = screen.getByTestId('increment-qty-dish-1');
    const decrementBtn = screen.getByTestId('decrement-qty-dish-1');

    fireEvent.click(incrementBtn);
    expect(screen.getByTestId('item-qty-dish-1')).toHaveTextContent('3');

    fireEvent.click(decrementBtn);
    expect(screen.getByTestId('item-qty-dish-1')).toHaveTextContent('2');

    const removeBtn = screen.getByTestId('remove-item-dish-1');
    fireEvent.click(removeBtn);
    expect(screen.queryByText('Ensalada César Vegana')).not.toBeInTheDocument();
    expect(screen.getByTestId('cart-empty-message')).toBeInTheDocument();
  });

  it('handles clear cart, close button, and checkout triggers', () => {
    renderWithCart([compliantItem], true);

    const closeBtn = screen.getByTestId('cart-drawer-close-button');
    fireEvent.click(closeBtn);
    expect(mockOnClose).toHaveBeenCalledTimes(1);

    const checkoutBtn = screen.getByTestId('cart-checkout-button');
    fireEvent.click(checkoutBtn);
    expect(mockOnCheckout).toHaveBeenCalledTimes(1);

    const clearBtn = screen.getByTestId('cart-clear-button');
    fireEvent.click(clearBtn);
    expect(screen.getByTestId('cart-empty-message')).toBeInTheDocument();
  });

  it('renders detailed financial breakdown per line item (name, unit price, quantity, line subtotal) and exact grand total (T026, COM-6.1.2, INV-2, FR-010)', () => {
    const itemA: CartItem = {
      dishId: 'dish-A',
      name: 'Ravioles de Ricota',
      price: 19.99,
      quantity: 3,
      isCompliant: true,
    };

    const itemB: CartItem = {
      dishId: 'dish-B',
      name: 'Tiramisú',
      price: 14.5,
      quantity: 2,
      isCompliant: true,
    };

    renderWithCart([itemA, itemB], true);

    // Verify item A line item breakdown
    expect(screen.getByText('Ravioles de Ricota')).toBeInTheDocument();
    expect(screen.getByTestId('item-qty-dish-A')).toHaveTextContent('3');
    // Unit price: 19.99
    expect(screen.getByText(/19,99|19\.99/)).toBeInTheDocument();
    // Line subtotal: 19.99 * 3 = 59.97
    expect(screen.getByText(/59,97|59\.97/)).toBeInTheDocument();

    // Verify item B line item breakdown
    expect(screen.getByText('Tiramisú')).toBeInTheDocument();
    expect(screen.getByTestId('item-qty-dish-B')).toHaveTextContent('2');
    // Unit price: 14.50
    expect(screen.getByText(/14,50|14\.50/)).toBeInTheDocument();
    // Line subtotal: 14.50 * 2 = 29.00
    expect(screen.getByText(/29,00|29\.00/)).toBeInTheDocument();

    // Verify exact grand total: 59.97 + 29.00 = 88.97
    expect(screen.getByTestId('cart-total-amount')).toHaveTextContent(/88,97|88\.97/);
  });
});
