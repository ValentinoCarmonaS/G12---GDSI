import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useDebouncedCartModifier } from '../../src/hooks/useDebouncedCartModifier';
import * as cartValidationApiModule from '../../src/api/cartValidationApi';

vi.mock('../../src/api/cartValidationApi', () => ({
  cartValidationApi: {
    validateStock: vi.fn(),
  },
}));

describe('useDebouncedCartModifier Hook (T032, US3, COM-6.1.3, INV-3, INV-9, FR-016)', () => {
  const defaultProps = {
    dishId: 'dish-123',
    tenantId: 'tenant-456',
    initialQuantity: 2,
    availableStock: 5,
    onCommitQuantity: vi.fn(),
    onRollback: vi.fn(),
    debounceMs: 300,
  };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('provides optimistic counter update immediately before debounce window elapses', () => {
    const { result } = renderHook(() => useDebouncedCartModifier(defaultProps));

    expect(result.current.quantity).toBe(2);

    act(() => {
      result.current.increment();
    });

    // Optimistic: increments immediately
    expect(result.current.quantity).toBe(3);
    // Not committed yet
    expect(defaultProps.onCommitQuantity).not.toHaveBeenCalled();
    expect(cartValidationApiModule.cartValidationApi.validateStock).not.toHaveBeenCalled();
  });

  it('batches rapid clicks and cancels trailing timers within 300 ms window', async () => {
    vi.mocked(cartValidationApiModule.cartValidationApi.validateStock).mockResolvedValueOnce({
      isValid: true,
      items: [
        {
          dishId: 'dish-123',
          isAvailable: true,
          availableStock: 5,
          requestedQuantity: 5,
        },
      ],
    });

    const { result } = renderHook(() => useDebouncedCartModifier(defaultProps));

    // Rapidly click increment 3 times: 2 -> 3 -> 4 -> 5
    act(() => {
      result.current.increment();
    });
    act(() => {
      vi.advanceTimersByTime(100);
      result.current.increment();
    });
    act(() => {
      vi.advanceTimersByTime(100);
      result.current.increment();
    });

    expect(result.current.quantity).toBe(5);
    // 200 ms passed total since start, API should not have been called yet
    expect(cartValidationApiModule.cartValidationApi.validateStock).not.toHaveBeenCalled();

    // Advance remaining 300 ms of the last debounce timer
    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    expect(cartValidationApiModule.cartValidationApi.validateStock).toHaveBeenCalledTimes(1);
    expect(cartValidationApiModule.cartValidationApi.validateStock).toHaveBeenCalledWith({
      tenantId: 'tenant-456',
      items: [{ dishId: 'dish-123', quantity: 5 }],
    });
    expect(defaultProps.onCommitQuantity).toHaveBeenCalledWith('dish-123', 5);
  });

  it('rolls back counter deterministically to last confirmed value on network failure (INV-9, FR-016)', async () => {
    const networkError = new Error('Network Connection Error');
    vi.mocked(cartValidationApiModule.cartValidationApi.validateStock).mockRejectedValueOnce(networkError);

    const onRollback = vi.fn();
    const { result } = renderHook(() =>
      useDebouncedCartModifier({
        ...defaultProps,
        onRollback,
      })
    );

    act(() => {
      result.current.increment();
    });
    expect(result.current.quantity).toBe(3);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    // Reverted back to confirmed initialQuantity (2)
    expect(result.current.quantity).toBe(2);
    expect(onRollback).toHaveBeenCalledWith('dish-123', 2, expect.any(Error));
  });

  it('rolls back counter when server reports item is not available due to stock exhaustion (INV-3, INV-9)', async () => {
    vi.mocked(cartValidationApiModule.cartValidationApi.validateStock).mockResolvedValueOnce({
      isValid: false,
      items: [
        {
          dishId: 'dish-123',
          isAvailable: false,
          availableStock: 2,
          requestedQuantity: 4,
        },
      ],
    });

    const onRollback = vi.fn();
    const { result } = renderHook(() =>
      useDebouncedCartModifier({
        ...defaultProps,
        onRollback,
      })
    );

    act(() => {
      result.current.increment(); // 3
      result.current.increment(); // 4
    });
    expect(result.current.quantity).toBe(4);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    // Reverts to 2
    expect(result.current.quantity).toBe(2);
    expect(onRollback).toHaveBeenCalled();
  });

  it('prevents incrementing beyond availableStock (INV-3, FR-014)', () => {
    const { result } = renderHook(() =>
      useDebouncedCartModifier({
        ...defaultProps,
        initialQuantity: 5,
        availableStock: 5,
      })
    );

    expect(result.current.isMaxStockReached).toBe(true);

    act(() => {
      result.current.increment();
    });

    // Does not exceed stock limit of 5
    expect(result.current.quantity).toBe(5);
  });
});
