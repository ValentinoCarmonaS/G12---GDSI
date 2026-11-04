import { describe, it, expect, vi, beforeEach } from 'vitest';
import { operationsApi } from '../../src/api/operationsApi';
import { apiClient } from '../../src/api/apiClient';
import type { UpdateSlotCapacityRequest } from '../../src/types/operations';

vi.mock('../../src/api/apiClient', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

const TENANT = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';

describe('operationsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('admin operating schedules', () => {
    it('GETs /admin/operating-schedules and unwraps the body', async () => {
      const body = [{ id: '1', dayOfWeek: 'MONDAY', openingTime: '12:00', closingTime: '16:00', isClosed: false, version: 0 }];
      vi.mocked(apiClient.get).mockResolvedValue({ data: body });

      const result = await operationsApi.getSchedules();

      expect(apiClient.get).toHaveBeenCalledWith('/admin/operating-schedules');
      expect(result).toEqual(body);
    });

    it('PUTs the full list to /admin/operating-schedules', async () => {
      const request = [
        { dayOfWeek: 'MONDAY' as const, openingTime: '12:00', closingTime: '16:00', isClosed: false, expectedVersion: 3 },
      ];
      vi.mocked(apiClient.put).mockResolvedValue({ data: [] });

      await operationsApi.updateSchedules(request);

      expect(apiClient.put).toHaveBeenCalledWith('/admin/operating-schedules', request);
    });
  });

  describe('admin slot capacities', () => {
    it('GETs /admin/slot-capacities with the date as a query param', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      await operationsApi.getSlotCapacities('2026-11-10');

      expect(apiClient.get).toHaveBeenCalledWith('/admin/slot-capacities', { params: { date: '2026-11-10' } });
    });

    it('PUTs a bulk list of many slots in a single request (no extra endpoint)', async () => {
      const bulk: UpdateSlotCapacityRequest[] = ['12:00', '12:15', '12:30'].map((startTime) => ({
        slotDate: '2026-11-10',
        startTime,
        maxCapacity: 50,
      }));
      vi.mocked(apiClient.put).mockResolvedValue({ data: [] });

      await operationsApi.updateSlotCapacities(bulk);

      expect(apiClient.put).toHaveBeenCalledTimes(1);
      expect(apiClient.put).toHaveBeenCalledWith('/admin/slot-capacities', bulk);
    });
  });

  describe('admin payment methods', () => {
    it('GETs /admin/payment-methods', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      await operationsApi.getAdminPaymentMethods();

      expect(apiClient.get).toHaveBeenCalledWith('/admin/payment-methods');
    });

    it('PUTs the toggle to /admin/payment-methods/{method} and returns the updated method', async () => {
      const updated = { id: '1', paymentMethod: 'STRIPE', displayName: 'Stripe', isEnabled: false, version: 1 };
      vi.mocked(apiClient.put).mockResolvedValue({ data: updated });

      const result = await operationsApi.togglePaymentMethod('STRIPE', { isEnabled: false, expectedVersion: 0 });

      expect(apiClient.put).toHaveBeenCalledWith('/admin/payment-methods/STRIPE', {
        isEnabled: false,
        expectedVersion: 0,
      });
      expect(result).toEqual(updated);
    });
  });

  describe('diner flows', () => {
    it('GETs the time slots of an establishment for a date', async () => {
      const body = { date: '2026-11-10', isClosed: false, timezone: 'America/Argentina/Buenos_Aires', slots: [] };
      vi.mocked(apiClient.get).mockResolvedValue({ data: body });

      const result = await operationsApi.getDinerTimeSlots(TENANT, '2026-11-10');

      expect(apiClient.get).toHaveBeenCalledWith(`/establishments/${TENANT}/time-slots`, {
        params: { date: '2026-11-10' },
      });
      expect(result).toEqual(body);
    });

    it('GETs the enabled payment methods of an establishment', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [{ paymentMethod: 'STRIPE', displayName: 'Stripe' }] });

      const result = await operationsApi.getDinerPaymentMethods(TENANT);

      expect(apiClient.get).toHaveBeenCalledWith(`/establishments/${TENANT}/payment-methods`);
      expect(result).toHaveLength(1);
    });

    it('POSTs the selected method to validate-payment (INV-9)', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: { valid: true } });

      const result = await operationsApi.validatePaymentMethod(TENANT, 'MERCADO_PAGO');

      expect(apiClient.post).toHaveBeenCalledWith(`/establishments/${TENANT}/checkout/validate-payment`, {
        paymentMethod: 'MERCADO_PAGO',
      });
      expect(result).toEqual({ valid: true });
    });

    it('does not swallow a 409 rejection: the caller must see the failure', async () => {
      const conflict = { response: { status: 409 } };
      vi.mocked(apiClient.post).mockRejectedValue(conflict);

      await expect(operationsApi.validatePaymentMethod(TENANT, 'STRIPE')).rejects.toBe(conflict);
    });
  });
});
