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
