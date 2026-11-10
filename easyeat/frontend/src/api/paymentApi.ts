import { apiClient } from './apiClient';
import type {
  PaymentInitiationRequest,
  PaymentInitiationResponse,
  CancelPaymentRequest,
  CancelPaymentResponse,
  OrderPaymentStatusResponse,
} from '../types/payment';

export const initiatePayment = async (
  payload: PaymentInitiationRequest
): Promise<PaymentInitiationResponse> => {
  const response = await apiClient.post<PaymentInitiationResponse>('/checkout/pay', payload);
  return response.data;
};

export const cancelPayment = async (
  payload: CancelPaymentRequest
): Promise<CancelPaymentResponse> => {
  const response = await apiClient.post<CancelPaymentResponse>('/checkout/cancel-payment', payload);
  return response.data;
};

export const getOrderPaymentStatus = async (
  orderId: string
): Promise<OrderPaymentStatusResponse> => {
  const response = await apiClient.get<OrderPaymentStatusResponse>(`/orders/${orderId}/payment-status`);
  return response.data;
};
