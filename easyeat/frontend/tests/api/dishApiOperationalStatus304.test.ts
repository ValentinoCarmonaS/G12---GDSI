import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { dishApi } from '@/api/dishApi';
import { apiClient } from '@/api/apiClient';
import { useChefDishesSync } from '@/hooks/useChefDishesSync';

vi.mock('@/api/apiClient', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe('dishApi.getOperationalStatus 304 & useChefDishesSync (T044, FR-004, D-04, Q2, C1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('envía If-None-Match y configura validateStatus para aceptar 304 sin rechazar la promesa', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      status: 304,
      headers: { etag: '"1a2b3c"' },
      data: '',
    });

    const result = await dishApi.getOperationalStatus('"1a2b3c"');

    expect(apiClient.get).toHaveBeenCalledTimes(1);
    const [url, config] = vi.mocked(apiClient.get).mock.calls[0];
    expect(url).toBe('/dishes/operational-status');
    expect(config?.headers).toEqual({ 'If-None-Match': '"1a2b3c"' });
    expect(typeof config?.validateStatus).toBe('function');
    expect(config?.validateStatus?.(200)).toBe(true);
    expect(config?.validateStatus?.(304)).toBe(true);
    expect(config?.validateStatus?.(401)).toBe(false);
    expect(config?.validateStatus?.(500)).toBe(false);

    expect(result).toEqual({
      etag: '"1a2b3c"',
      items: [],
      notModified: true,
    });
  });

  it('en 304 Not Modified sin cabecera ETag devuelta conserva el ifNoneMatch enviado', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      status: 304,
      headers: {},
      data: '',
    });

    const result = await dishApi.getOperationalStatus('"cached-etag"');

    expect(result).toEqual({
      etag: '"cached-etag"',
      items: [],
      notModified: true,
    });
  });

  it('useChefDishesSync conserva items intactos cuando la revalidación devuelve 304 notModified', async () => {
    const initialItems = [
      {
        dishId: 'dish-1',
        pausedByChef: false,
        hasStock: true,
        isActiveAdmin: true,
        status: 'PUBLISHED',
      },
    ];

    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({
        status: 200,
        headers: { etag: '"etag-v1"' },
        data: { items: initialItems },
      })
      .mockResolvedValueOnce({
        status: 304,
        headers: { etag: '"etag-v1-normalized"' },
        data: '',
      });

    const { result } = renderHook(() => useChefDishesSync(true));

    await act(async () => {
      // Primera carga (200 OK)
    });
    expect(result.current.items).toEqual(initialItems);

    await act(async () => {
      vi.advanceTimersByTime(2500);
    });

    expect(result.current.items).toEqual(initialItems);
  });
});
