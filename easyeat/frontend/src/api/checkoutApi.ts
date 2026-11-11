import apiClient from './apiClient';
import {
  PaymentConfirmationRequest,
  PaymentConfirmationResponse,
  PendingPaymentRequest,
  PendingPaymentResponse,
  ZeroAmountOrderRequest,
  ZeroAmountOrderResponse,
} from '../types/checkout';

export const confirmPayment = async (
  request: PaymentConfirmationRequest
): Promise<PaymentConfirmationResponse> => {
  const response = await apiClient.post<PaymentConfirmationResponse>(
    '/checkout/confirm-payment',
    request
  );
  return response.data;
};

export const registerPendingPayment = async (
  request: PendingPaymentRequest
): Promise<PendingPaymentResponse> => {
  const response = await apiClient.post<PendingPaymentResponse>(
    '/checkout/pending-payment',
    request
  );
  return response.data;
};

export const confirmZeroAmountOrder = async (
  request: ZeroAmountOrderRequest
): Promise<ZeroAmountOrderResponse> => {
  const response = await apiClient.post<ZeroAmountOrderResponse>(
    '/checkout/confirm-zero-amount',
    request
  );
  return response.data;
};
