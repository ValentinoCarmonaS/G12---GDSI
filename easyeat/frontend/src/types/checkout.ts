import type { OrderStatus } from './order';
import type { PaymentStatus } from './payment';
import type { KdsTicketSummary } from './kds';

/**
 * Request payload to confirm an approved checkout payment.
 */
export interface PaymentConfirmationRequest {
  orderId: string;
  gatewayPaymentId: string;
  status: string;
}

/**
 * Response payload returned upon successful payment confirmation.
 */
export interface PaymentConfirmationResponse {
  orderId: string;
  orderStatus: OrderStatus;
  paymentStatus: PaymentStatus;
  stockDebited: boolean;
  kdsTicket?: KdsTicketSummary;
  idempotentReplay?: boolean;
}

/**
 * Request payload when payment gateway returns a pending status.
 */
export interface PendingPaymentRequest {
  orderId: string;
  gatewayPaymentId: string;
  statusDetail?: string;
}

/**
 * Response payload when order transitions to PENDING_ACCREDITATION.
 */
export interface PendingPaymentResponse {
  orderId: string;
  orderStatus: OrderStatus;
  paymentStatus: PaymentStatus;
  stockDebited: boolean;
  kdsQueued: boolean;
  reservationFrozen: boolean;
  message?: string;
}

/**
 * Request payload for 100% discounted / zero-amount orders bypassing payment gateway.
 */
export interface ZeroAmountOrderRequest {
  orderId: string;
}

/**
 * Response payload for confirmed 100% discounted / zero-amount orders.
 */
export interface ZeroAmountOrderResponse {
  orderId: string;
  orderStatus: OrderStatus;
  totalAmount: number;
  gatewayBypassed: boolean;
  stockDebited: boolean;
  kdsTicket?: KdsTicketSummary;
  message?: string;
}
