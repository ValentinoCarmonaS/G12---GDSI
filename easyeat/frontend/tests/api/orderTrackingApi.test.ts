import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '../../src/api/apiClient';
import {
  markTicketReadyForPickup,
  orderTrackingApi,
} from '../../src/api/orderTrackingApi';

vi.mock('../../src/api/apiClient', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
  },
}));

describe('orderTrackingApi.markTicketReadyForPickup (T013, US1, Contrato 01)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('posts to /kitchen/tickets/{ticketId}/ready-for-pickup and returns KdsTicketResponse', async () => {
    const ticketId = '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d';
    const expectedPayload = {
      ticketId,
      orderId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
      ticketNumber: 104,
      status: 'READY_FOR_PICKUP' as const,
      readyAt: '2026-11-15T19:30:00.123456Z',
    };

    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: expectedPayload,
    });

    const result = await markTicketReadyForPickup(ticketId);

    expect(apiClient.post).toHaveBeenCalledTimes(1);
    expect(apiClient.post).toHaveBeenCalledWith(
      `/kitchen/tickets/${ticketId}/ready-for-pickup`
    );
    expect(result).toEqual(expectedPayload);
  });

  it('exposes the same consolidated function via orderTrackingApi.markTicketReadyForPickup', async () => {
    const ticketId = 'a15c329b-81d3-4e89-b620-7f2cb14197e2';
    const expectedPayload = {
      ticketId,
      orderId: '50e932b1-12c8-4ec7-9104-58a47de0281b',
      ticketNumber: 105,
      status: 'READY_FOR_PICKUP' as const,
      readyAt: '2026-11-15T19:33:45Z',
    };

    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: expectedPayload,
    });

    const result = await orderTrackingApi.markTicketReadyForPickup(ticketId);

    expect(apiClient.post).toHaveBeenCalledWith(
      `/kitchen/tickets/${ticketId}/ready-for-pickup`
    );
    expect(result).toEqual(expectedPayload);
  });
});
