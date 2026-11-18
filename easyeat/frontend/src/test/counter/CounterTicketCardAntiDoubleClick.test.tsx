import { act, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CounterTicketCard } from '../../components/counter/CounterTicketCard';
import type { CounterReadyOrder } from '../../types/counter';

/**
 * Spec 022 T033 (US3, ENC-3.3.1 AC-2, RF-008, invariant 10, edge case 4, research D-08): however fast the
 * operator presses "ENTREGAR PEDIDO", the card dispatches a single `completeDelivery` call. The guard must hold
 * even when several clicks land in the same event-loop turn, before React re-renders the disabled button.
 */
describe('CounterTicketCard anti double click (Spec 022 T033)', () => {
  const order: CounterReadyOrder = {
    ticketId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
    orderId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
    ticketNumber: 104,
    timeSlot: { start: '13:00', end: '13:15' },
    readyAt: '2026-11-15T19:30:00Z',
    customer: { userId: 'c71e2289-4b68-45e0-a7d1-e6e7d6928e01', fullName: 'Valentino Carmona', photoUrl: null },
    securityCode: '4821',
    items: [{ quantity: 2, dishName: 'Hamburguesa Completa con Papas' }],
  };
  const nowMs = Date.parse(order.readyAt) + 60_000;

  const renderCard = (onDeliver: (orderId: string) => Promise<void>) =>
    render(
      <CounterTicketCard order={order} isExpanded={true} onToggle={vi.fn()} onDeliver={onDeliver} nowMs={nowMs} />
    );

  const pending = () => new Promise<void>(() => {});

  it('sends one request for a double click', async () => {
    const user = userEvent.setup();
    const onDeliver = vi.fn(pending);
    renderCard(onDeliver);

    await user.dblClick(screen.getByTestId('counter-card-delivery-button'));

    expect(onDeliver).toHaveBeenCalledTimes(1);
  });

  it('sends one request for a rapid triple click', () => {
    const onDeliver = vi.fn(pending);
    renderCard(onDeliver);
    const button = screen.getByTestId('counter-card-delivery-button');

    fireEvent.click(button);
    fireEvent.click(button);
    fireEvent.click(button);

    expect(onDeliver).toHaveBeenCalledTimes(1);
  });

  it('sends one request when clicks land in the same turn, before the disabled state renders', () => {
    const onDeliver = vi.fn(pending);
    renderCard(onDeliver);
    const button = screen.getByTestId('counter-card-delivery-button');

    act(() => {
      button.click();
      button.click();
    });

    expect(onDeliver).toHaveBeenCalledTimes(1);
  });

  it('ignores further clicks after the server confirms the delivery', async () => {
    const onDeliver = vi.fn().mockResolvedValue(undefined);
    renderCard(onDeliver);
    const button = screen.getByTestId('counter-card-delivery-button');

    await act(async () => {
      fireEvent.click(button);
    });
    act(() => {
      button.click();
    });

    expect(onDeliver).toHaveBeenCalledTimes(1);
  });
});
