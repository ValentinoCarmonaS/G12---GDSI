import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useKdsQueuePolling } from '../../hooks/useKdsQueuePolling';
import * as kitchenKdsApi from '../../api/kitchenKdsApi';

vi.mock('../../api/kitchenKdsApi', () => ({
  fetchKitchenOrders: vi.fn(),
}));

describe('T007 [P] [US1]: useKdsQueuePolling Hook Unit Tests', () => {
  const mockOrdersResponse: kitchenKdsApi.KitchenOrderPageResponse = {
    content: [
      {
        ticketId: 't-1',
        ticketNumber: 101,
        orderId: 'o-1',
        status: 'QUEUED',
        fulfillmentMode: 'IMMEDIATE',
        pickupDate: '2026-11-15',
        timeSlotStart: '12:00:00',
        timeSlotEnd: '12:15:00',
        queuedAt: '2026-11-15T15:00:00Z',
        startedAt: null,
        items: [{ dishId: 'd-1', dishName: 'Burger', quantity: 1 }],
      },
    ],
    pageable: { pageNumber: 0, pageSize: 100 },
    totalElements: 1,
    totalPages: 1,
  };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      writable: true,
      configurable: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('debe realizar polling silencioso cada 4000ms con activeQueue=true', async () => {
    vi.mocked(kitchenKdsApi.fetchKitchenOrders).mockResolvedValue(mockOrdersResponse);

    const { result } = renderHook(() => useKdsQueuePolling());

    expect(result.current.isLoading).toBe(true);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(kitchenKdsApi.fetchKitchenOrders).toHaveBeenCalledTimes(1);
    expect(kitchenKdsApi.fetchKitchenOrders).toHaveBeenCalledWith(undefined, 0, 100, true);
    expect(result.current.tickets).toEqual(mockOrdersResponse.content);
    expect(result.current.isLoading).toBe(false);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000);
    });

    expect(kitchenKdsApi.fetchKitchenOrders).toHaveBeenCalledTimes(2);
  });

  it('debe suspender el polling cuando la pestaña se oculta (visibilityState === "hidden")', async () => {
    vi.mocked(kitchenKdsApi.fetchKitchenOrders).mockResolvedValue(mockOrdersResponse);

    renderHook(() => useKdsQueuePolling());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(kitchenKdsApi.fetchKitchenOrders).toHaveBeenCalledTimes(1);

    // Ocultar pestaña
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(8000);
    });

    // No debe haber hecho más llamadas mientras está oculta
    expect(kitchenKdsApi.fetchKitchenOrders).toHaveBeenCalledTimes(1);

    // Volver a visible
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    // Se reanuda inmediatamente
    expect(kitchenKdsApi.fetchKitchenOrders).toHaveBeenCalledTimes(2);
  });

  it('debe cancelar el timer al desmontar el hook', async () => {
    vi.mocked(kitchenKdsApi.fetchKitchenOrders).mockResolvedValue(mockOrdersResponse);

    const { unmount } = renderHook(() => useKdsQueuePolling());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(kitchenKdsApi.fetchKitchenOrders).toHaveBeenCalledTimes(1);

    unmount();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(8000);
    });

    expect(kitchenKdsApi.fetchKitchenOrders).toHaveBeenCalledTimes(1);
  });

  it('debe aplicar backoff ante fallos de red sin romper el estado previo', async () => {
    vi.mocked(kitchenKdsApi.fetchKitchenOrders)
      .mockResolvedValueOnce(mockOrdersResponse)
      .mockRejectedValueOnce(new Error('Network error'));

    const { result } = renderHook(() => useKdsQueuePolling());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(kitchenKdsApi.fetchKitchenOrders).toHaveBeenCalledTimes(1);
    expect(result.current.tickets).toEqual(mockOrdersResponse.content);

    // Siguiente tick falla
    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000);
    });

    expect(kitchenKdsApi.fetchKitchenOrders).toHaveBeenCalledTimes(2);
    // Preserva datos previos ante fallo silencioso
    expect(result.current.tickets).toEqual(mockOrdersResponse.content);
    expect(result.current.error).toBeDefined();
  });
});
