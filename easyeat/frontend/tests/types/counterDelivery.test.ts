import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import type { CompleteDeliveryResponse, CounterReadyOrder } from '../../src/types/counter';

/**
 * Spec 022 T002: counter delivery types.
 * - Contract 02 invariant 3 / Edge case 10: a ready order may lack a security code (`null`).
 * - Contract 01: terminal payload of POST /counter/orders/{orderId}/complete-delivery.
 */
describe('Counter delivery types (Spec 022 T002)', () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../../src/types/counter.ts'), 'utf-8');

  it('declares securityCode as nullable on CounterReadyOrder (edge case 10)', () => {
    expect(source).toMatch(/securityCode:\s*string\s*\|\s*null;/);
  });

  it('exports CompleteDeliveryResponse without monetary fields', () => {
    expect(source).toContain('export interface CompleteDeliveryResponse');
    expect(source).not.toMatch(/\b(unitPrice|subtotal|totalAmount|currency)\b/);
  });

  it('accepts a ready order whose security code is absent', () => {
    const orderWithoutPin: CounterReadyOrder = {
      ticketId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
      orderId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
      ticketNumber: 7,
      timeSlot: { start: '13:00', end: '13:15' },
      readyAt: '2026-11-16T18:15:00Z',
      customer: {
        userId: 'd98f1214-9271-4899-8ab3-772c21980a34',
        fullName: 'Lucía Morales',
        photoUrl: null,
      },
      securityCode: null,
      items: [{ quantity: 1, dishName: 'Agua Mineral 500ml' }],
    };

    expect(orderWithoutPin.securityCode).toBeNull();
  });

  it('models the first and the idempotent complete-delivery responses identically (contract 01)', () => {
    const first: CompleteDeliveryResponse = {
      orderId: 'b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
      ticketNumber: 42,
      status: 'COMPLETED',
      completedAt: '2026-11-15T18:30:00Z',
      message: 'Entrega completada exitosamente.',
    };
    const retry: CompleteDeliveryResponse = {
      ...first,
      message: 'La orden ya se encontraba completada. Respuesta idempotente.',
    };

    expect(retry.status).toBe('COMPLETED');
    expect(retry.completedAt).toBe(first.completedAt);
  });
});
