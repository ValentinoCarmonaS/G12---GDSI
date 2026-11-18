import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CounterTicketCard } from '../../components/counter/CounterTicketCard';
import type { CounterReadyOrder } from '../../types/counter';

/**
 * Spec 022 T032 (US3, ENC-3.3.1 AC-1, RF-006..RF-009, SC-004, research D-08/D-09): one click on
 * "ENTREGAR PEDIDO" acts directly, with no confirmation modal, disables the control in the same event and shows
 * the green "Entrega Confirmada" feedback before the server answers. As agreed in the 2026-11-18 grill-me, the
 * green state lasts while the request travels; on success the page removes the card (T031).
 */
describe('CounterTicketCard direct delivery action (Spec 022 T032)', () => {
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

  /** A request that stays in flight until the test settles it. */
  const deferred = () => {
    let resolve!: () => void;
    const promise = new Promise<void>((res) => {
      resolve = res;
    });
    return { promise, resolve };
  };

  const renderCard = (onDeliver: (orderId: string) => Promise<void>) =>
    render(
      <CounterTicketCard order={order} isExpanded={true} onToggle={vi.fn()} onDeliver={onDeliver} nowMs={nowMs} />
    );

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts with "ENTREGAR PEDIDO" enabled and no confirmation shown', () => {
    renderCard(vi.fn().mockResolvedValue(undefined));

    const button = screen.getByTestId('counter-card-delivery-button');
    expect(button).toBeEnabled();
    expect(button).toHaveTextContent('ENTREGAR PEDIDO');
    expect(screen.queryByText('Entrega Confirmada')).not.toBeInTheDocument();
  });

  it('dispatches the delivery directly on one click, without any confirmation dialog', () => {
    const confirmSpy = vi.spyOn(window, 'confirm');
    const onDeliver = vi.fn(() => deferred().promise);
    renderCard(onDeliver);

    fireEvent.click(screen.getByTestId('counter-card-delivery-button'));

    expect(onDeliver).toHaveBeenCalledTimes(1);
    expect(onDeliver).toHaveBeenCalledWith(order.orderId);
    expect(confirmSpy).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('disables the control in the same click event, before the server answers', () => {
    renderCard(vi.fn(() => deferred().promise));

    fireEvent.click(screen.getByTestId('counter-card-delivery-button'));

    const button = screen.getByTestId('counter-card-delivery-button');
    expect(button).toBeDisabled();
    expect(button.className).toContain('cursor-not-allowed');
  });

  it('shows the green "Entrega Confirmada" feedback inside the card at once, replacing the call to action', () => {
    renderCard(vi.fn(() => deferred().promise));

    fireEvent.click(screen.getByTestId('counter-card-delivery-button'));

    const container = screen.getByTestId('counter-card-delivery-container');
    const confirmed = within(container).getByTestId('counter-card-delivery-confirmed');
    expect(confirmed).toHaveTextContent('Entrega Confirmada');
    expect(confirmed.className).toMatch(/emerald/);
    expect(within(container).queryByText('ENTREGAR PEDIDO')).not.toBeInTheDocument();
  });

  it('keeps the confirmation and the disabled control once the server confirms the delivery', async () => {
    const request = deferred();
    renderCard(vi.fn(() => request.promise));

    fireEvent.click(screen.getByTestId('counter-card-delivery-button'));
    await act(async () => {
      request.resolve();
      await request.promise;
    });

    expect(screen.getByTestId('counter-card-delivery-button')).toBeDisabled();
    expect(screen.getByTestId('counter-card-delivery-confirmed')).toHaveTextContent('Entrega Confirmada');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
