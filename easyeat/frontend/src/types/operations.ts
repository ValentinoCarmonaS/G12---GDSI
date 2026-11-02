/**
 * Payload types for the Restaurant Operation Configuration (spec 010).
 *
 * Mirrors `specs/010-restaurant-operation-config/contracts/openapi.yaml`. Times travel as
 * `HH:mm` or `HH:mm:ss` strings and dates as `YYYY-MM-DD`; the server, not the browser, decides
 * what the current time is (D-001), so no type here carries a client-computed instant.
 *
 * Covers the three user stories (schedules, slot capacity, online payment methods) because the
 * admin screen and the diner flows share one contract.
 */

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export type PaymentMethod = 'MERCADO_PAGO' | 'STRIPE';

export interface OperatingSchedule {
  id: string;
  dayOfWeek: DayOfWeek;
  openingTime: string;
  closingTime: string;
  isClosed: boolean;
  version: number;
}

/** `expectedVersion` is omitted for a day that has never been configured. */
export interface UpdateScheduleRequest {
  dayOfWeek: DayOfWeek;
  openingTime: string;
  closingTime: string;
  isClosed: boolean;
  expectedVersion?: number | null;
}

export interface SlotCapacity {
  id: string;
  slotDate: string;
  startTime: string;
  endTime: string;
  maxCapacity: number;
  approvedOrdersCount: number;
  isSoldOut: boolean;
  version: number;
}

/** One entry of the PUT list; a bulk apply is the same list with one entry per 15-minute slot. */
export interface UpdateSlotCapacityRequest {
  slotDate: string;
  startTime: string;
  maxCapacity: number;
  expectedVersion?: number | null;
}

export interface AdminPaymentMethod {
  id: string;
  paymentMethod: PaymentMethod;
  displayName: string;
  isEnabled: boolean;
  version: number;
}

export interface TogglePaymentMethodRequest {
  isEnabled: boolean;
  expectedVersion?: number | null;
}

export type DinerSlotStatus = 'AVAILABLE' | 'SOLD_OUT';

export interface DinerTimeSlot {
  startTime: string;
  endTime: string;
  status: DinerSlotStatus;
}

export interface DinerTimeSlots {
  date: string;
  isClosed: boolean;
  closureReason?: string | null;
  timezone: string;
  slots: DinerTimeSlot[];
}

export interface DinerPaymentMethod {
  paymentMethod: PaymentMethod;
  displayName: string;
}

export interface ValidatePaymentResult {
  valid: boolean;
}

/** RFC 7807 body returned with 400 and 409 responses. */
export interface OperationsProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
}
