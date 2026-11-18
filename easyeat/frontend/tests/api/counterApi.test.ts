import { beforeEach, describe, expect, it, vi } from 'vitest';
import apiClient from '../../src/api/apiClient';
import { completeDelivery, counterApi, fetchCounterOrders } from '../../src/api/counterApi';
import type { CounterReadyOrder } from '../../src/types/counter';

vi.mock('../../src/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('counterApi — fetchCounterOrders (T003)', () => {
  const sampleOrders: CounterReadyOrder[] = [
    {
      ticketId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
      orderId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
      ticketNumber: 104,
      timeSlot: { start: '13:00', end: '13:15' },
      readyAt: '2026-11-15T19:30:00Z',
      customer: {
        userId: 'c71e2289-4b68-45e0-a7d1-e6e7d6928e01',
        fullName: 'Valentino Carmona',
        photoUrl: '/api/v1/profile/users/c71e2289-4b68-45e0-a7d1-e6e7d6928e01/avatar',
      },
      securityCode: '4821',
      items: [
        { quantity: 2, dishName: 'Hamburguesa Completa con Papas' },
        { quantity: 1, dishName: 'Limonada con Menta y Jengibre' },
      ],
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('requests GET /counter/orders with default READY_FOR_PICKUP status and no-cache headers', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: sampleOrders,
      headers: {},
    });

    const result = await fetchCounterOrders();

    expect(apiClient.get).toHaveBeenCalledTimes(1);
    expect(apiClient.get).toHaveBeenCalledWith('/counter/orders', {
      params: { status: 'READY_FOR_PICKUP' },
      headers: {
        'Cache-Control': 'no-cache',
        Pragma: 'no-cache',
      },
      signal: undefined,
    });
    expect(result).toEqual(sampleOrders);
  });

  it('forwards establishmentId, custom status, AbortSignal, and reports server Date header when provided', async () => {
    const controller = new AbortController();
    const onServerDate = vi.fn();

    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: sampleOrders,
      headers: {
        date: 'Sun, 15 Nov 2026 16:35:00 GMT',
      },
    });

    const result = await counterApi.fetchCounterOrders({
      establishmentId: 'est-123',
      status: 'READY_FOR_PICKUP',
      signal: controller.signal,
      onServerDate,
    });

    expect(apiClient.get).toHaveBeenCalledWith('/counter/orders', {
      params: {
        status: 'READY_FOR_PICKUP',
        establishmentId: 'est-123',
      },
      headers: {
        'Cache-Control': 'no-cache',
        Pragma: 'no-cache',
      },
      signal: controller.signal,
    });
    expect(onServerDate).toHaveBeenCalledWith(Date.parse('Sun, 15 Nov 2026 16:35:00 GMT'));
    expect(result).toEqual(sampleOrders);
  });
});

describe('counterApi — completeDelivery (Spec 022 T030, contract 01)', () => {
  const orderId = 'b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('sends POST /counter/orders/{orderId}/complete-delivery with no body and returns the payload', async () => {
    const payload = {
      orderId,
      ticketNumber: 42,
      status: 'COMPLETED' as const,
      completedAt: '2026-11-15T18:30:00Z',
      message: 'Entrega completada exitosamente.',
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: payload });

    const result = await completeDelivery(orderId);

    expect(apiClient.post).toHaveBeenCalledTimes(1);
    expect(apiClient.post).toHaveBeenCalledWith(`/counter/orders/${orderId}/complete-delivery`);
    expect(result).toEqual(payload);
  });

  it('is exposed on the counterApi object', () => {
    expect(counterApi.completeDelivery).toBe(completeDelivery);
  });

  it('propagates HTTP errors (409, 404, 403) to the caller', async () => {
    const conflict = Object.assign(new Error('Request failed with status code 409'), { response: { status: 409 } });
    vi.mocked(apiClient.post).mockRejectedValueOnce(conflict);

    await expect(completeDelivery(orderId)).rejects.toBe(conflict);
  });
});
