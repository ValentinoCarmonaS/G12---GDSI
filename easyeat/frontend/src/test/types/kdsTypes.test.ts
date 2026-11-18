import { describe, it, expect } from 'vitest';
import type {
  KdsTicket,
  KdsOrderItem,
  KdsStartPreparationResponse,
  KdsTicketStatus,
  FulfillmentMode,
} from '../../types/kds';

describe('KDS TypeScript Domain Types (T002)', () => {
  it('validates KdsOrderItem interface shape', () => {
    const item: KdsOrderItem = {
      dishId: 'd-1',
      dishName: 'Hamburguesa',
      quantity: 2,
    };
    expect(item.dishId).toBe('d-1');
    expect(item.dishName).toBe('Hamburguesa');
    expect(item.quantity).toBe(2);
  });

  it('validates KdsTicket interface shape with all required fields', () => {
    const ticket: KdsTicket = {
      ticketId: 't-101',
      ticketNumber: 101,
      orderId: 'o-500',
      status: 'QUEUED',
      fulfillmentMode: 'IMMEDIATE',
      pickupDate: '2026-11-16',
      timeSlotStart: '12:00:00',
      timeSlotEnd: '12:15:00',
      queuedAt: '2026-11-16T15:00:00Z',
      startedAt: null,
      items: [
        {
          dishId: 'd-1',
          dishName: 'Hamburguesa',
          quantity: 2,
        },
      ],
    };

    expect(ticket.ticketId).toBe('t-101');
    expect(ticket.status).toBe('QUEUED');
    expect(ticket.startedAt).toBeNull();
    expect(ticket.items).toHaveLength(1);
  });

  it('validates KdsStartPreparationResponse interface shape', () => {
    const response: KdsStartPreparationResponse = {
      ticketId: 't-101',
      ticketNumber: 101,
      orderId: 'o-500',
      status: 'IN_PREPARATION',
      startedAt: '2026-11-16T15:05:00Z',
    };

    expect(response.ticketId).toBe('t-101');
    expect(response.status).toBe('IN_PREPARATION');
    expect(response.startedAt).toBe('2026-11-16T15:05:00Z');
  });

  it('validates status type values', () => {
    const statuses: KdsTicketStatus[] = ['QUEUED', 'IN_PREPARATION', 'READY_FOR_PICKUP', 'COMPLETED'];
    expect(statuses).toHaveLength(4);

    const modes: FulfillmentMode[] = ['IMMEDIATE', 'SCHEDULED'];
    expect(modes).toHaveLength(2);
  });
});
