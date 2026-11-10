import apiClient from './apiClient';
import { KdsDashboardSummaryResponse, KdsTicketStatus } from '../types/kds';

export interface KitchenOrderPageResponse {
  content: Array<{
    ticketId: string;
    ticketNumber: number;
    orderId: string;
    status: KdsTicketStatus;
    fulfillmentMode: string;
    pickupDate: string;
    timeSlotStart: string;
    timeSlotEnd: string;
    queuedAt: string;
    items: Array<{
      dishId: string;
      dishName: string;
      quantity: number;
    }>;
  }>;
  pageable: {
    pageNumber: number;
    pageSize: number;
  };
  totalElements: number;
  totalPages: number;
}

export const fetchKitchenOrders = async (
  status?: KdsTicketStatus,
  page = 0,
  size = 20
): Promise<KitchenOrderPageResponse> => {
  const params: Record<string, any> = { page, size };
  if (status) {
    params.status = status;
  }
  const response = await apiClient.get<KitchenOrderPageResponse>('/api/v1/kitchen/orders', {
    params,
  });
  return response.data;
};

export const fetchDashboardSummary = async (): Promise<KdsDashboardSummaryResponse> => {
  const response = await apiClient.get<KdsDashboardSummaryResponse>(
    '/api/v1/kitchen/dashboard/summary'
  );
  return response.data;
};
