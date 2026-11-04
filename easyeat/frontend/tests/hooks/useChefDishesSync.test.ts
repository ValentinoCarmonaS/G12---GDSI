import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useChefDishesSync } from '../../src/hooks/useChefDishesSync';
import { dishApi } from '@/api/dishApi';

vi.mock('@/api/dishApi', () => ({
  dishApi: {
    getOperationalStatus: vi.fn(),
  },
}));

describe('useChefDishesSync (T042, D-006, INV-18)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('polls GET /api/v1/dishes/operational-status every 2500ms and updates items on ETag change', async () => {
    const mockItems = [
      {
        dishId: 'd-1',
        pausedByChef: false,
        hasStock: true,
        isActiveAdmin: true,
        status: 'PUBLISHED',
      },
    ];

    vi.mocked(dishApi.getOperationalStatus).mockResolvedValueOnce({
      etag: 'etag-1',
      items: mockItems,
    } as any);

    const { result } = renderHook(() => useChefDishesSync(true));

    await act(async () => {
      // First fetch triggered on mount
    });

    expect(result.current.items).toEqual(mockItems);
    expect(dishApi.getOperationalStatus).toHaveBeenCalledTimes(1);

    // Now advance 2500 ms
    const updatedItems = [
      {
        dishId: 'd-1',
        pausedByChef: true,
        hasStock: true,
        isActiveAdmin: true,
        status: 'PUBLISHED',
      },
    ];

    vi.mocked(dishApi.getOperationalStatus).mockResolvedValueOnce({
      etag: 'etag-2',
      items: updatedItems,
    } as any);

    await act(async () => {
      vi.advanceTimersByTime(2500);
    });

    expect(dishApi.getOperationalStatus).toHaveBeenCalledWith('etag-1');
    expect(result.current.items).toEqual(updatedItems);
  });
});
