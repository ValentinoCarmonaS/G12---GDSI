import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useRealtimeAvailability } from '@/hooks/useRealtimeAvailability';
import { dinerCatalogApi } from '@/api/dinerCatalogApi';

vi.mock('@/api/dinerCatalogApi', () => ({
  dinerCatalogApi: {
    getCatalogAvailability: vi.fn(),
  },
}));

describe('useRealtimeAvailability hook (T040, US3, COM-4.1.2)', () => {
  const tenantId = 'tenant-uuid-123';

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    Object.defineProperty(document, 'hidden', {
      writable: true,
      configurable: true,
      value: false,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('polls availability at 2.5s intervals and updates availability on 200 OK', async () => {
    const mockAvailability1 = { 'dish-1': true, 'dish-2': false };
    const mockAvailability2 = { 'dish-1': false, 'dish-2': false };

    vi.mocked(dinerCatalogApi.getCatalogAvailability)
      .mockResolvedValueOnce({
        status: 200,
        data: { availability: mockAvailability1 },
        etag: '"hash-1"',
      })
      .mockResolvedValueOnce({
        status: 200,
        data: { availability: mockAvailability2 },
        etag: '"hash-2"',
      });

    const { result } = renderHook(() =>
      useRealtimeAvailability({ tenantId, intervalMs: 2500 })
    );

    // Initial tick
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(dinerCatalogApi.getCatalogAvailability).toHaveBeenCalledWith(tenantId, null);
    expect(result.current.availability).toEqual(mockAvailability1);

    // Advance 2.5 seconds for next poll
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2500);
    });

    expect(dinerCatalogApi.getCatalogAvailability).toHaveBeenCalledWith(tenantId, '"hash-1"');
    expect(result.current.availability).toEqual(mockAvailability2);
  });

  it('retains availability state and updates nothing on 304 Not Modified', async () => {
    const mockAvailability = { 'dish-1': true };

    vi.mocked(dinerCatalogApi.getCatalogAvailability)
      .mockResolvedValueOnce({
        status: 200,
        data: { availability: mockAvailability },
        etag: '"hash-stable"',
      })
      .mockResolvedValueOnce({
        status: 304,
        data: null,
        etag: '"hash-stable"',
      });

    const { result } = renderHook(() =>
      useRealtimeAvailability({ tenantId, intervalMs: 2500 })
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(result.current.availability).toEqual(mockAvailability);

    // Poll 2 gives 304
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2500);
    });

    expect(dinerCatalogApi.getCatalogAvailability).toHaveBeenCalledTimes(2);
    expect(result.current.availability).toEqual(mockAvailability);
  });

  it('pauses polling when document is hidden and resumes when visible', async () => {
    vi.mocked(dinerCatalogApi.getCatalogAvailability).mockResolvedValue({
      status: 304,
      data: null,
      etag: '"hash-stable"',
    });

    renderHook(() =>
      useRealtimeAvailability({ tenantId, intervalMs: 2500 })
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(dinerCatalogApi.getCatalogAvailability).toHaveBeenCalledTimes(1);

    // Tab becomes hidden
    act(() => {
      Object.defineProperty(document, 'hidden', { value: true, configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    });

    // Advance 5 seconds - no calls should occur while hidden
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });
    expect(dinerCatalogApi.getCatalogAvailability).toHaveBeenCalledTimes(1);

    // Tab becomes visible again
    act(() => {
      Object.defineProperty(document, 'hidden', { value: false, configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(dinerCatalogApi.getCatalogAvailability).toHaveBeenCalledTimes(2);
  });

  it('maintains last known availability and sets isConnectionLost after 3 consecutive failures', async () => {
    const mockAvailability = { 'dish-1': true };
    const onErrorThreshold = vi.fn();
    const onRecovery = vi.fn();

    vi.mocked(dinerCatalogApi.getCatalogAvailability)
      .mockResolvedValueOnce({
        status: 200,
        data: { availability: mockAvailability },
        etag: '"hash-ok"',
      })
      .mockRejectedValueOnce(new Error('Network error 1'))
      .mockRejectedValueOnce(new Error('Network error 2'))
      .mockRejectedValueOnce(new Error('Network error 3'))
      .mockResolvedValueOnce({
        status: 304,
        data: null,
        etag: '"hash-ok"',
      });

    const { result } = renderHook(() =>
      useRealtimeAvailability({
        tenantId,
        intervalMs: 2500,
        onErrorThreshold,
        onRecovery,
      })
    );

    // Initial success
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.availability).toEqual(mockAvailability);
    expect(result.current.isConnectionLost).toBe(false);

    // Fail 1
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2500);
    });
    expect(result.current.isConnectionLost).toBe(false);
    expect(result.current.availability).toEqual(mockAvailability); // retained

    // Fail 2
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2500);
    });
    expect(result.current.isConnectionLost).toBe(false);

    // Fail 3 -> threshold reached
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2500);
    });
    expect(result.current.isConnectionLost).toBe(true);
    expect(onErrorThreshold).toHaveBeenCalledTimes(1);
    expect(result.current.availability).toEqual(mockAvailability); // still retained

    // Recovery
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2500);
    });
    expect(result.current.isConnectionLost).toBe(false);
    expect(onRecovery).toHaveBeenCalledTimes(1);
  });
});
