import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CartItemWithBadge } from '../../src/components/cart/CartItemWithBadge';
import { CartItem } from '../../src/types/cart';

describe('CartItemWithBadge Component (T033, US3, COM-6.1.3, INV-3, AC-3.3, FR-011..FR-015)', () => {
  const baseItem: CartItem = {
    dishId: 'dish-1',
    name: 'Bife de Chorizo',
    price: 15.5,
    quantity: 2,
    availableStock: 5,
    isCompliant: true,
  };

  it('renders dish details, formatted unit price, and line subtotal', () => {
    render(
      <CartItemWithBadge
        item={baseItem}
        onUpdateQuantity={vi.fn()}
        onRemove={vi.fn()}
      />
    );

    expect(screen.getByText('Bife de Chorizo')).toBeInTheDocument();
    expect(screen.getByTestId('item-qty-dish-1')).toHaveTextContent('2');
    // Unit price ($15.50) and Line total ($31.00)
    expect(screen.getByText(/\$15\.50|\$ 15,50/)).toBeInTheDocument();
    expect(screen.getByText(/\$31\.00|\$ 31,00/)).toBeInTheDocument();
  });

  it('dispatches onUpdateQuantity with quantity + 1 when increment button is clicked', () => {
    const handleUpdate = vi.fn();
    render(
      <CartItemWithBadge
        item={baseItem}
        onUpdateQuantity={handleUpdate}
        onRemove={vi.fn()}
      />
    );

    const incrementBtn = screen.getByTestId('increment-qty-dish-1');
    expect(incrementBtn).not.toBeDisabled();

    fireEvent.click(incrementBtn);
    expect(handleUpdate).toHaveBeenCalledWith('dish-1', 3);
  });

  it('disables increment button when quantity reaches availableStock (INV-3, FR-014)', () => {
    const atMaxStockItem: CartItem = {
      ...baseItem,
      quantity: 5,
      availableStock: 5,
    };

    render(
      <CartItemWithBadge
        item={atMaxStockItem}
        onUpdateQuantity={vi.fn()}
        onRemove={vi.fn()}
      />
    );

    const incrementBtn = screen.getByTestId('increment-qty-dish-1');
    expect(incrementBtn).toBeDisabled();
  });

  it('removes item completely when decrement is clicked on quantity 1 (AC-3.3, FR-015)', () => {
    const singleUnitItem: CartItem = {
      ...baseItem,
      quantity: 1,
    };

    const handleRemove = vi.fn();
    const handleUpdate = vi.fn();

    render(
      <CartItemWithBadge
        item={singleUnitItem}
        onUpdateQuantity={handleUpdate}
        onRemove={handleRemove}
      />
    );

    const decrementBtn = screen.getByTestId('decrement-qty-dish-1');
    fireEvent.click(decrementBtn);

    // Either onRemove is called directly or onUpdateQuantity called with 0
    const wasRemoved =
      handleRemove.mock.calls.length > 0 ||
      handleUpdate.mock.calls.some((call) => call[0] === 'dish-1' && call[1] === 0);

    expect(wasRemoved).toBe(true);
  });

  it('displays "Stock insuficiente" badge when quantity exceeds availableStock (INV-3, FR-014a)', () => {
    const excessItem: CartItem = {
      ...baseItem,
      quantity: 4,
      availableStock: 2, // Kitchen stock dropped
    };

    render(
      <CartItemWithBadge
        item={excessItem}
        onUpdateQuantity={vi.fn()}
        onRemove={vi.fn()}
      />
    );

    expect(screen.getByTestId('insufficient-stock-badge-dish-1')).toBeInTheDocument();
    expect(screen.getByText(/stock insuficiente/i)).toBeInTheDocument();
  });
});
