import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { CounterTicketCard } from '../../components/counter/CounterTicketCard';
import type { CounterReadyOrder } from '../../types/counter';

/**
 * Spec 022 T034 (US3, RF-018, invariant 11, research D-14, contract 01 §3.3–3.5): when "ENTREGAR PEDIDO" fails,
 * the card withdraws the green feedback, re-enables the button, stays on the board and shows a non-intrusive
 * notice inside itself. Decisions of the 2026-11-18 grill-me: every failure rolls back the same way; a network
 * error or a 5xx shows the D-14 text, a 4xx shows the server's `detail` (D-14 text when empty); the notice is an
 * inline `role="alert"` banner with no timer, cleared by the next click.
 */
describe('CounterTicketCard error recovery (Spec 022 T034)', () => {
  const COMMUNICATION_ERROR = 'Error de comunicación al registrar la entrega. Por favor, reintentá.';
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

  const httpError = (status: number, detail?: string) =>
    Object.assign(new Error(`Request failed with status code ${status}`), {
      isAxiosError: true,
      response: { status, data: detail === undefined ? {} : { detail } },
    });

  const renderCard = (onDeliver: (orderId: string) => Promise<void>) =>
    render(
      <CounterTicketCard order={order} isExpanded={true} onToggle={vi.fn()} onDeliver={onDeliver} nowMs={nowMs} />
    );

  const pressDeliver = async () => {
    await act(async () => {
      fireEvent.click(screen.getByTestId('counter-card-delivery-button'));
    });
  };

  it('on a network failure, shows the communication notice, re-enables the button and keeps the card', async () => {
    renderCard(vi.fn().mockRejectedValue(new Error('Network Error')));

    await pressDeliver();

    const card = screen.getByTestId(`counter-ticket-card-${order.ticketId}`);
    const notice = within(card).getByRole('alert');
    expect(notice).toHaveTextContent(COMMUNICATION_ERROR);
    expect(within(card).getByTestId('counter-card-expanded-body')).toBeInTheDocument();
    const button = within(card).getByTestId('counter-card-delivery-button');
    expect(button).toBeEnabled();
    expect(button).toHaveTextContent('ENTREGAR PEDIDO');
    expect(within(card).queryByTestId('counter-card-delivery-confirmed')).not.toBeInTheDocument();
  });

  it('on a 5xx, shows the communication notice instead of the server detail', async () => {
    renderCard(vi.fn().mockRejectedValue(httpError(500, 'NullPointerException en CounterOrderService')));

    await pressDeliver();

    expect(screen.getByRole('alert')).toHaveTextContent(COMMUNICATION_ERROR);
    expect(screen.queryByText(/NullPointerException/)).not.toBeInTheDocument();
  });

  it('on a 409 INVALID_ORDER_STATE, shows the server detail and rolls back the same way', async () => {
    const detail = 'No se puede entregar una orden que se encuentra en estado CANCELLED. Debe estar en estado READY_FOR_PICKUP.';
    renderCard(vi.fn().mockRejectedValue(httpError(409, detail)));

    await pressDeliver();

    expect(screen.getByRole('alert')).toHaveTextContent(detail);
    expect(screen.getByTestId('counter-card-delivery-button')).toBeEnabled();
  });

  it('on a 4xx without detail, falls back to the communication notice', async () => {
    renderCard(vi.fn().mockRejectedValue(httpError(404)));

    await pressDeliver();

    expect(screen.getByRole('alert')).toHaveTextContent(COMMUNICATION_ERROR);
  });

  it('clears the notice on the next click and retries the delivery', async () => {
    const onDeliver = vi
      .fn<(orderId: string) => Promise<void>>()
      .mockRejectedValueOnce(new Error('Network Error'))
      .mockReturnValueOnce(new Promise<void>(() => {}));
    renderCard(onDeliver);

    await pressDeliver();
    expect(screen.getByRole('alert')).toBeInTheDocument();

    await pressDeliver();

    expect(onDeliver).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByTestId('counter-card-delivery-confirmed')).toHaveTextContent('Entrega Confirmada');
  });

  it('never uses a blocking browser alert', async () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    renderCard(vi.fn().mockRejectedValue(new Error('Network Error')));

    await pressDeliver();

    expect(alertSpy).not.toHaveBeenCalled();
    alertSpy.mockRestore();
  });
});
