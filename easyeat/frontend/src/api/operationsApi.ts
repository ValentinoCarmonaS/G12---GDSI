import { apiClient } from './apiClient';
import type {
  AdminPaymentMethod,
  DinerPaymentMethod,
  DinerTimeSlots,
  OperatingSchedule,
  PaymentMethod,
  SlotCapacity,
  TogglePaymentMethodRequest,
  UpdateScheduleRequest,
  UpdateSlotCapacityRequest,
  ValidatePaymentResult,
} from '@/types/operations';

/**
 * Typed client for the Restaurant Operation Configuration endpoints (spec 010).
 *
 * Thin by intent: it shapes requests and unwraps bodies, nothing else. Errors are not caught, so a
 * 403, a 409 on a stale `expectedVersion` or a 409 on a disabled payment method reaches the
 * caller instead of becoming a working-looking default. Admin endpoints take the establishment
 * from the authenticated session; the diner endpoints name it in the path.
 */
export const operationsApi = {
  async getSchedules(): Promise<OperatingSchedule[]> {
    const response = await apiClient.get<OperatingSchedule[]>('/admin/operating-schedules');
    return response.data;
  },

  async updateSchedules(request: UpdateScheduleRequest[]): Promise<OperatingSchedule[]> {
    const response = await apiClient.put<OperatingSchedule[]>('/admin/operating-schedules', request);
    return response.data;
  },

  async getSlotCapacities(date: string): Promise<SlotCapacity[]> {
    const response = await apiClient.get<SlotCapacity[]>('/admin/slot-capacities', { params: { date } });
    return response.data;
  },

  /** Also the bulk-apply path: one entry per 15-minute slot, in a single request (SC-005). */
  async updateSlotCapacities(request: UpdateSlotCapacityRequest[]): Promise<SlotCapacity[]> {
    const response = await apiClient.put<SlotCapacity[]>('/admin/slot-capacities', request);
    return response.data;
  },

  async getAdminPaymentMethods(): Promise<AdminPaymentMethod[]> {
    const response = await apiClient.get<AdminPaymentMethod[]>('/admin/payment-methods');
    return response.data;
  },

  async togglePaymentMethod(
    method: PaymentMethod,
    request: TogglePaymentMethodRequest,
  ): Promise<AdminPaymentMethod> {
    const response = await apiClient.put<AdminPaymentMethod>(`/admin/payment-methods/${method}`, request);
    return response.data;
  },

  async getDinerTimeSlots(tenantId: string, date: string): Promise<DinerTimeSlots> {
    const response = await apiClient.get<DinerTimeSlots>(`/establishments/${tenantId}/time-slots`, {
      params: { date },
    });
    return response.data;
  },

  async getDinerPaymentMethods(tenantId: string): Promise<DinerPaymentMethod[]> {
    const response = await apiClient.get<DinerPaymentMethod[]>(`/establishments/${tenantId}/payment-methods`);
    return response.data;
  },

  /** INV-9: live revalidation at confirmation; a disabled method answers 409. */
  async validatePaymentMethod(tenantId: string, paymentMethod: PaymentMethod): Promise<ValidatePaymentResult> {
    const response = await apiClient.post<ValidatePaymentResult>(
      `/establishments/${tenantId}/checkout/validate-payment`,
      { paymentMethod },
    );
    return response.data;
  },
};
