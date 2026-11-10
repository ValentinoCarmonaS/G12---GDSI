import type { FulfillmentMode, OrderStatus } from './order';

/**
 * Métodos de pago soportados en EasyEat.
 */
export type PaymentMethod = 'MERCADO_PAGO' | 'STRIPE';

/**
 * Estados del ciclo de vida de una reserva temporal conjunta (cupo + stock).
 */
export type ReservationStatus = 'ACTIVE' | 'CONFIRMED' | 'RELEASED' | 'EXPIRED';

/**
 * Motivos formales de liberación de una reserva temporal.
 */
export type ReleaseReason = 'REJECTED' | 'CANCELLED_BY_USER' | 'EXPIRED' | 'GATEWAY_ERROR';

/**
 * Estados transaccionales de pago delegados a pasarela.
 */
export type PaymentStatus =
  | 'IN_PROCESS'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'FAILED'
  | 'EXPIRED';

/**
 * Códigos de error específicos de pago y reserva.
 */
export type PaymentErrorCode =
  | 'SLOT_SOLD_OUT'
  | 'INSUFFICIENT_STOCK'
  | 'PAYMENT_IN_PROCESS'
  | 'GATEWAY_UNAVAILABLE'
  | 'RESERVATION_EXPIRED'
  | 'INVALID_PAYMENT_STATE';

/**
 * Item de la orden al iniciar el checkout de pago.
 */
export interface PaymentOrderItemRequest {
  dishId: string;
  quantity: number;
}

/**
 * Payload para POST /api/v1/checkout/pay
 */
export interface PaymentInitiationRequest {
  establishmentId: string;
  fulfillmentMode: FulfillmentMode;
  paymentMethod?: PaymentMethod;
  pickupDate?: string | null;
  timeSlotStart?: string | null;
  timeSlotEnd?: string | null;
  items: PaymentOrderItemRequest[];
}

/**
 * Respuesta de POST /api/v1/checkout/pay (201 Created)
 */
export interface PaymentInitiationResponse {
  orderId: string;
  reservationId: string;
  paymentId: string;
  gatewayPreferenceId: string;
  redirectUrl: string;
  sandbox: boolean;
  expiresAt: string;
  totalAmount: number;
  currency: string;
}

/**
 * Payload para POST /api/v1/checkout/cancel-payment
 */
export interface CancelPaymentRequest {
  orderId: string;
}

/**
 * Respuesta de POST /api/v1/checkout/cancel-payment (200 OK)
 */
export interface CancelPaymentResponse {
  orderId: string;
  reservationStatus: ReservationStatus;
  paymentStatus: PaymentStatus;
  message: string;
}

/**
 * Información de reserva temporal en estado consolidado.
 */
export interface OrderReservationStatusDetail {
  id: string;
  status: ReservationStatus;
  expiresAt: string;
  remainingSeconds: number;
  releaseReason: ReleaseReason | null;
}

/**
 * Información de pago en estado consolidado.
 */
export interface OrderPaymentStatusDetail {
  id: string;
  status: PaymentStatus;
  statusDetail: string | null;
  redirectUrl: string | null;
  sandbox: boolean;
}

/**
 * Información de entrega/franja horaria en estado consolidado.
 */
export interface OrderFulfillmentStatusDetail {
  mode: FulfillmentMode;
  pickupDate: string | null;
  timeSlotStart: string | null;
  timeSlotEnd: string | null;
}

/**
 * Respuesta consolidada de GET /api/v1/orders/{orderId}/payment-status (200 OK)
 */
export interface OrderPaymentStatusResponse {
  orderId: string;
  orderStatus: OrderStatus;
  reservation: OrderReservationStatusDetail | null;
  payment: OrderPaymentStatusDetail | null;
  fulfillment: OrderFulfillmentStatusDetail;
  totalAmount: number;
  currency: string;
  serverTime: string;
}

