import axios from 'axios';
import type { OrderConfirmationTrackingResponse, OrderLiveTrackingResponse } from '../types/orderTracking';

export const orderTrackingApi = {
  getConfirmationTracking: async (orderId: string): Promise<OrderConfirmationTrackingResponse> => {
    const response = await axios.get<OrderConfirmationTrackingResponse>(
      `/api/v1/orders/${orderId}/confirmation-tracking`
    );
    return response.data;
  },

  getLiveTracking: async (orderId: string): Promise<OrderLiveTrackingResponse> => {
    const response = await axios.get<OrderLiveTrackingResponse>(
      `/api/v1/orders/${orderId}/live-tracking`
    );
    return response.data;
  },
};
