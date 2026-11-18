import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import type {
  CounterCustomerSummary,
  CounterOrderItem,
  CounterOrdersPollingState,
  CounterReadyOrder,
  CounterTimeSlot,
} from '../../src/types/counter';

describe('Counter domain types (T002)', () => {
  const counterTypesFilePath = path.resolve(__dirname, '../../src/types/counter.ts');

  it('defines and exports CounterTimeSlot, CounterCustomerSummary, CounterOrderItem, CounterReadyOrder and CounterOrdersPollingState', () => {
    expect(fs.existsSync(counterTypesFilePath)).toBe(true);
    const source = fs.readFileSync(counterTypesFilePath, 'utf-8');

    expect(source).toContain('export interface CounterTimeSlot');
    expect(source).toContain('export interface CounterCustomerSummary');
    expect(source).toContain('export interface CounterOrderItem');
    expect(source).toContain('export interface CounterReadyOrder');
    expect(source).toContain('export interface CounterOrdersPollingState');

    // RF-013 & Invariante 10: Strict monetary omission
    expect(source).not.toMatch(/\b(unitPrice|subtotal|totalAmount|currency)\b/);
  });

  it('satisfies structural contracts for counter ready order and polling state', () => {
    const timeSlot: CounterTimeSlot = {
      start: '13:00',
      end: '13:15',
    };

    const customerWithPhoto: CounterCustomerSummary = {
      userId: 'c71e2289-4b68-45e0-a7d1-e6e7d6928e01',
      fullName: 'Valentino Carmona',
      photoUrl: '/api/v1/profile/users/c71e2289-4b68-45e0-a7d1-e6e7d6928e01/avatar',
    };

    const customerWithoutPhoto: CounterCustomerSummary = {
      userId: 'd98f1214-9271-4899-8ab3-772c21980a34',
      fullName: 'Lucía Morales',
      photoUrl: null,
    };

    const item: CounterOrderItem = {
      quantity: 2,
      dishName: 'Hamburguesa Completa con Papas',
    };

    const order: CounterReadyOrder = {
      ticketId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
      orderId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
      ticketNumber: 104,
      timeSlot,
      readyAt: '2026-11-15T19:30:00Z',
      customer: customerWithPhoto,
      securityCode: '4821',
      items: [item],
    };

    const pollingState: CounterOrdersPollingState = {
      orders: [order],
      isLoading: false,
      isDisconnected: false,
      serverOffsetMs: 0,
      refresh: async () => {},
    };

    expect(order.ticketNumber).toBe(104);
    expect(order.timeSlot.start).toBe('13:00');
    expect(customerWithoutPhoto.photoUrl).toBeNull();
    expect(pollingState.orders).toHaveLength(1);
    expect(pollingState.isDisconnected).toBe(false);
  });
});
