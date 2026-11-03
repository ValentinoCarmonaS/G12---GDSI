import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { CartProvider } from '../../src/context/CartContext';
import { useCart } from '../../src/hooks/useCart';
import { catalogApi } from '../../src/api/catalogApi';
import { EnrichedDish } from '../../src/types/catalog';

vi.mock('../../src/api/catalogApi', () => ({
  catalogApi: {
    evaluateCartCompliance: vi.fn(),
  },
}));

describe('CartContext & useCart (T055, US3, FR-015, FR-021, FR-022)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <CartProvider>{children}</CartProvider>
  );

  const mockCompliantDish: EnrichedDish = {
    id: 'dish-1',
    tenantId: 'tenant-1',
    name: 'Ensalada César',
    description: 'Lechuga y croutons',
    price: 4500,
    isActive: true,
    isCompliant: true,
    conflictingRestrictions: [],
  };

  const mockConflictingDish: EnrichedDish = {
    id: 'dish-2',
    tenantId: 'tenant-1',
    name: 'Tarta de Frutillas con Crema',
    description: 'Contiene crema de leche',
    price: 3200,
    isActive: true,
    isCompliant: false,
    conflictingRestrictions: ['DAIRY'],
  };

  it('adds items to cart and computes total amount, count, and compliance state', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    act(() => {
      result.current.addItem(mockCompliantDish);
    });

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].tenantId).toBe('tenant-1');
    expect(result.current.totalCount).toBe(1);
    expect(result.current.totalAmount).toBe(4500);
    expect(result.current.hasConflictingItems).toBe(false);

    // Add conflicting item with riskConfirmed
    act(() => {
      result.current.addItem(mockConflictingDish, true);
    });

    expect(result.current.items).toHaveLength(2);
    expect(result.current.totalCount).toBe(2);
    expect(result.current.totalAmount).toBe(7700);
    expect(result.current.hasConflictingItems).toBe(true);
    expect(result.current.items[1].riskConfirmed).toBe(true);

    // Verify sessionStorage sync
    const stored = JSON.parse(sessionStorage.getItem('easyeat_cart') || '[]');
    expect(stored).toHaveLength(2);
  });

  it('increments quantity when adding same dish multiple times', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    act(() => {
      result.current.addItem(mockCompliantDish);
      result.current.addItem(mockCompliantDish);
    });

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].quantity).toBe(2);
    expect(result.current.totalAmount).toBe(9000);
  });

  it('adds items with specific quantity and accumulates onto existing dish without duplicate rows (T021, INV-1, INV-3)', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    act(() => {
      // Add 3 units of mockCompliantDish
      result.current.addItem(mockCompliantDish, 3);
    });

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].quantity).toBe(3);
    expect(result.current.totalCount).toBe(3);
    expect(result.current.totalAmount).toBe(4500 * 3);

    // Accumulate 2 more units of the same dish
    act(() => {
      result.current.addItem(mockCompliantDish, 2);
    });

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].quantity).toBe(5);
    expect(result.current.totalCount).toBe(5);
    expect(result.current.totalAmount).toBe(4500 * 5);

    // Verify sessionStorage persists the accumulated quantity
    const stored = JSON.parse(sessionStorage.getItem('easyeat_cart') || '[]');
    expect(stored).toHaveLength(1);
    expect(stored[0].dishId).toBe(mockCompliantDish.id);
    expect(stored[0].quantity).toBe(5);
  });

  it('broadcasts auto-dismissing toast feedback when item is added (T021, COM-6.1.1 AC-1.1)', () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useCart(), { wrapper });

    act(() => {
      result.current.addItem(mockCompliantDish, 2);
    });

    expect(result.current.toastMessage).toBeDefined();
    expect(result.current.toastMessage).toContain('Ensalada César');
    expect(result.current.toastMessage).toContain('2');

    // Auto-dismiss after 3000ms
    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(result.current.toastMessage).toBeNull();
    vi.useRealTimers();
  });

  it('enforces availableStock upper bound when accumulating quantity (T021, INV-3)', () => {
    const { result } = renderHook(() => useCart(), { wrapper });
    const dishWithStock: EnrichedDish & { availableStock?: number } = {
      ...mockCompliantDish,
      availableStock: 4,
    };

    act(() => {
      result.current.addItem(dishWithStock, 3);
    });
    expect(result.current.items[0].quantity).toBe(3);

    // Attempting to add 3 more when stock is 4 should clamp to availableStock (4)
    act(() => {
      result.current.addItem(dishWithStock, 3);
    });

    expect(result.current.items[0].quantity).toBe(4);
    expect(result.current.totalCount).toBe(4);
  });

  it('updates quantity and removes item when quantity reaches 0', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    act(() => {
      result.current.addItem(mockCompliantDish);
    });

    act(() => {
      result.current.updateQuantity(mockCompliantDish.id, 5);
    });
    expect(result.current.items[0].quantity).toBe(5);

    act(() => {
      result.current.updateQuantity(mockCompliantDish.id, 0);
    });
    expect(result.current.items).toHaveLength(0);
  });

  it('removes item explicitly and clears cart', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    act(() => {
      result.current.addItem(mockCompliantDish);
      result.current.addItem(mockConflictingDish);
    });
    expect(result.current.items).toHaveLength(2);

    act(() => {
      result.current.removeItem(mockCompliantDish.id);
    });
    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].dishId).toBe(mockConflictingDish.id);

    act(() => {
      result.current.clearCart();
    });
    expect(result.current.items).toHaveLength(0);
    expect(sessionStorage.getItem('easyeat_cart')).toBeNull();
  });

  it('proactively re-evaluates compliance against backend API (FR-015)', async () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    act(() => {
      result.current.addItem(mockCompliantDish);
    });

    // Mock API response indicating dish-1 now conflicts with newly added restriction CELIAC
    vi.mocked(catalogApi.evaluateCartCompliance).mockResolvedValueOnce({
      items: [
        {
          dishId: mockCompliantDish.id,
          isCompliant: false,
          conflictingRestrictions: ['CELIAC'],
        },
      ],
    });

    await act(async () => {
      await result.current.reEvaluateCompliance();
    });

    expect(catalogApi.evaluateCartCompliance).toHaveBeenCalledWith({
      dishIds: [mockCompliantDish.id],
    });
    expect(result.current.items[0].isCompliant).toBe(false);
    expect(result.current.items[0].conflictingRestrictions).toEqual(['CELIAC']);
  });
});
