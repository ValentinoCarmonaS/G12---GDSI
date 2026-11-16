import apiClient from './apiClient';
import {
  KdsDashboardSummaryResponse,
  KdsDelayIncrementResponse,
  KdsStartPreparationResponse,
  KdsTicketStatus,
} from '../types/kds';

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
    startedAt: string | null;
    specificDelayMinutes?: number;
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
  size = 20,
  activeQueue = false
): Promise<KitchenOrderPageResponse> => {
  const params: Record<string, any> = { page, size };
  if (status) {
    params.status = status;
  }
  if (activeQueue) {
    params.activeQueue = true;
  }
  const response = await apiClient.get<KitchenOrderPageResponse>('/kitchen/orders', {
    params,
  });
  return response.data;
};

export const fetchDashboardSummary = async (): Promise<KdsDashboardSummaryResponse> => {
  const response = await apiClient.get<KdsDashboardSummaryResponse>(
    '/kitchen/dashboard/summary'
  );
  return response.data;
};

export const startTicketPreparation = async (
  ticketId: string
): Promise<KdsStartPreparationResponse> => {
  const response = await apiClient.post<KdsStartPreparationResponse>(
    `/kitchen/tickets/${ticketId}/start-preparation`
  );
  return response.data;
};

export const addTicketDelay = async (
  ticketId: string
): Promise<KdsDelayIncrementResponse> => {
  const response = await apiClient.post<KdsDelayIncrementResponse>(
    `/kitchen/tickets/${ticketId}/add-delay`
  );
  return response.data;
};
