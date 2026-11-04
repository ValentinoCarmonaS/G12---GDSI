import { apiClient } from './apiClient';
import {
  CreateOrderRequest,
  OrderDetailResponse,
  OrderSummaryResponse,
  PickupSlotCatalogResponse,
} from '../types/order';

/**
 * Fetches available 15-minute pickup slots for an establishment on a given date.
 */
export async function fetchPickupSlots(
  establishmentId: string,
  date?: string
): Promise<PickupSlotCatalogResponse> {
  const params = date ? { date } : {};
  const response = await apiClient.get<PickupSlotCatalogResponse>(
    `/establishments/${establishmentId}/pickup-slots`,
    { params }
  );
  return response.data;
}

/**
 * Creates a new order in PENDING_PAYMENT status and reserves a time slot.
 */
export async function createOrder(
  request: CreateOrderRequest
): Promise<OrderDetailResponse> {
  const response = await apiClient.post<any>('/orders', request);
  const data = response.data;
  return { ...data, orderId: data.orderId || data.id };
}

/**
 * Retrieves the details and current TTL countdown status of an order.
 */
export async function getOrderById(orderId: string): Promise<OrderDetailResponse> {
  const response = await apiClient.get<any>(`/orders/${orderId}`);
  const data = response.data;
  return { ...data, orderId: data.orderId || data.id };
}

/**
 * Cancels a pending order voluntarily, releasing its slot capacity immediately.
 */
export async function cancelOrder(orderId: string): Promise<OrderSummaryResponse> {
  const response = await apiClient.post<OrderSummaryResponse>(`/orders/${orderId}/cancel`);
  const data = response.data;
  return { ...data, orderId: data.orderId || data.id };
}
