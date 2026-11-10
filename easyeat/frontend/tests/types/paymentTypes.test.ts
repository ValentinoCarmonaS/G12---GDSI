import { describe, it, expect } from 'vitest';
import type {
  PaymentMethod,
  ReservationStatus,
  ReleaseReason,
  PaymentStatus,
  PaymentInitiationRequest,
  PaymentInitiationResponse,
  CancelPaymentRequest,
  CancelPaymentResponse,
  OrderPaymentStatusResponse,
} from '../../src/types/payment';

describe('Payment types definition (T005)', () => {
  it('should accept valid payment and reservation domain models', () => {
    const method: PaymentMethod = 'MERCADO_PAGO';
    const resStatus: ReservationStatus = 'ACTIVE';
    const releaseReason: ReleaseReason = 'CANCELLED_BY_USER';
    const payStatus: PaymentStatus = 'IN_PROCESS';

    const req: PaymentInitiationRequest = {
      establishmentId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
      fulfillmentMode: 'SCHEDULED',
      paymentMethod: method,
      pickupDate: '2026-11-10',
      timeSlotStart: '13:00:00',
      timeSlotEnd: '13:15:00',
      items: [{ dishId: 'dish-1', quantity: 2 }],
    };

    const resp: PaymentInitiationResponse = {
      orderId: 'order-1',
      reservationId: 'res-1',
      paymentId: 'pay-1',
      gatewayPreferenceId: 'pref-1',
      redirectUrl: 'https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-1',
      sandbox: true,
      expiresAt: '2026-11-09T02:25:00Z',
      totalAmount: 15500.0,
      currency: 'ARS',
    };

    const cancelReq: CancelPaymentRequest = {
      orderId: 'order-1',
    };

    const cancelResp: CancelPaymentResponse = {
      orderId: 'order-1',
      reservationStatus: 'RELEASED',
      paymentStatus: 'CANCELLED',
      message: 'Intento de pago cancelado',
    };

    const statusResp: OrderPaymentStatusResponse = {
      orderId: 'order-1',
      orderStatus: 'PENDING_PAYMENT',
      reservation: {
        id: 'res-1',
        status: resStatus,
        expiresAt: '2026-11-09T02:25:00Z',
        remainingSeconds: 600,
        releaseReason: null,
      },
      payment: {
        id: 'pay-1',
        status: payStatus,
        statusDetail: null,
        redirectUrl: 'https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-1',
        sandbox: true,
      },
      fulfillment: {
        mode: 'SCHEDULED',
        pickupDate: '2026-11-10',
        timeSlotStart: '13:00:00',
        timeSlotEnd: '13:15:00',
      },
      totalAmount: 15500.0,
      currency: 'ARS',
      serverTime: '2026-11-09T02:15:00Z',
    };

    expect(method).toBe('MERCADO_PAGO');
    expect(resStatus).toBe('ACTIVE');
    expect(releaseReason).toBe('CANCELLED_BY_USER');
    expect(payStatus).toBe('IN_PROCESS');
    expect(req.items).toHaveLength(1);
    expect(resp.orderId).toBe('order-1');
    expect(cancelReq.orderId).toBe('order-1');
    expect(cancelResp.reservationStatus).toBe('RELEASED');
    expect(statusResp.payment?.status).toBe('IN_PROCESS');
  });
});

