import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useRealtimeAvailability } from '../../src/hooks/useRealtimeAvailability';
import { dinerCatalogApi } from '@/api/dinerCatalogApi';

vi.mock('@/api/dinerCatalogApi', () => ({
  dinerCatalogApi: {
    getCatalogAvailability: vi.fn(),
  },
}));

describe('useRealtimeAvailability (T045, INV-15, D-006)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('propagates chef pause status changes to diner availability map within SLA', async () => {
    vi.mocked(dinerCatalogApi.getCatalogAvailability).mockResolvedValueOnce({
      status: 200,
      etag: 'etag-dish-active',
      data: { availability: { 'dish-1': true } },
    } as any);

    const { result } = renderHook(() =>
      useRealtimeAvailability({ tenantId: 'tenant-100', intervalMs: 2500 })
    );

    await act(async () => {
      // initial poll
    });

    expect(result.current.availability['dish-1']).toBe(true);

    // Chef pauses dish: availability becomes false
    vi.mocked(dinerCatalogApi.getCatalogAvailability).mockResolvedValueOnce({
      status: 200,
      etag: 'etag-dish-paused',
      data: { availability: { 'dish-1': false } },
    } as any);

    await act(async () => {
      vi.advanceTimersByTime(2500);
    });

    expect(result.current.availability['dish-1']).toBe(false);
  });
});
