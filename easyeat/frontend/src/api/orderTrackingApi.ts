import { apiClient } from './apiClient';
import type {
  OrderConfirmationTrackingResponse,
  OrderLiveTrackingResponse,
  KdsDelayAdjustmentRequest,
  KdsDelayAdjustmentResponse,
  KdsTicketReadyResponse,
} from '../types/orderTracking';

export const orderTrackingApi = {
  /**
   * Retrieves payment confirmation and tracking details for the green screen and order summary.
   */
  getConfirmationTracking: async (orderId: string): Promise<OrderConfirmationTrackingResponse> => {
    const response = await apiClient.get<OrderConfirmationTrackingResponse>(
      `/orders/${orderId}/confirmation-tracking`
    );
    return response.data;
  },

  /**
   * Retrieves dynamic live tracking status, KDS queue saturation and estimated time remaining.
   */
  getLiveTracking: async (orderId: string): Promise<OrderLiveTrackingResponse> => {
    const response = await apiClient.get<OrderLiveTrackingResponse>(
      `/orders/${orderId}/live-tracking`
    );
    return response.data;
  },

  /**
   * Updates global kitchen preparation delay modifier (US2 - personal KDS).
   */
  adjustKitchenDelay: async (
    request: KdsDelayAdjustmentRequest
  ): Promise<KdsDelayAdjustmentResponse> => {
    const response = await apiClient.put<KdsDelayAdjustmentResponse>(
      '/kitchen/delay',
      request
    );
    return response.data;
  },

  /**
   * Marks a kitchen ticket as completed and ready for pickup at the counter (US2).
   */
  markTicketReadyForPickup: async (ticketId: string): Promise<KdsTicketReadyResponse> => {
    const response = await apiClient.post<KdsTicketReadyResponse>(
      `/kitchen/tickets/${ticketId}/ready-for-pickup`
    );
    return response.data;
  },
};
