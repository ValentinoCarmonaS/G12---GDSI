import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { CounterWorkspacePage } from '@/pages/CounterWorkspacePage';
import { authApi } from '@/api/authApi';
import { counterApi } from '@/api/counterApi';
import * as AuthModule from '@/hooks/useAuth';
import * as CounterPollingModule from '@/hooks/useCounterOrdersPolling';
import type { CounterReadyOrder } from '@/types/counter';

vi.mock('@/hooks/useAuth');

/**
 * Spec 022 T031 (US2, RF-016, edge case 11): once the counter confirms a delivery, its card leaves the board
 * while the other cards keep their expanded state (`expandedTicketIds`, keyed by `ticketId`). The button wiring is
 * the minimal one agreed in the 2026-11-18 grill-me; US3 (T035/T036) adds the submitting state and the error notice.
 */
describe('CounterWorkspacePage delivery removal (Spec 022 T031)', () => {
  const order104: CounterReadyOrder = {
    ticketId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
    orderId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
    ticketNumber: 104,
    timeSlot: { start: '13:00', end: '13:15' },
    readyAt: '2026-11-15T19:30:00Z',
    customer: { userId: 'c71e2289-4b68-45e0-a7d1-e6e7d6928e01', fullName: 'Valentino Carmona', photoUrl: null },
    securityCode: '4821',
    items: [{ quantity: 2, dishName: 'Hamburguesa Completa con Papas' }],
  };
  const order105: CounterReadyOrder = {
    ticketId: 'a15c329b-81d3-4e89-b620-7f2cb14197e2',
    orderId: '50e932b1-12c8-4ec7-9104-58a47de0281b',
    ticketNumber: 105,
    timeSlot: { start: '13:15', end: '13:30' },
    readyAt: '2026-11-15T19:33:45Z',
    customer: { userId: 'd98f1214-9271-4899-8ab3-772c21980a34', fullName: 'Lucía Morales', photoUrl: null },
    securityCode: '9102',
    items: [{ quantity: 1, dishName: 'Ensalada Caesar con Pollo' }],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: { id: 'user-enc-1', email: 'enc@easyeat.test', role: 'ENCARGADO', tenantId: 'tenant-enc-1', tokenVersion: 1 },
      token: 'jwt-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    } as unknown as ReturnType<typeof AuthModule.useAuth>);
    vi.spyOn(CounterPollingModule, 'useCounterOrdersPolling').mockReturnValue({
      orders: [order104, order105],
      isLoading: false,
      isDisconnected: false,
      serverOffsetMs: 0,
      refresh: vi.fn(),
    });
  });

  const renderPage = () =>
    render(
      <MemoryRouter>
        <CounterWorkspacePage />
      </MemoryRouter>
    );

  const expandBoth = async (user: ReturnType<typeof userEvent.setup>) => {
    for (const ticketId of [order104.ticketId, order105.ticketId]) {
      const card = screen.getByTestId(`counter-ticket-card-${ticketId}`);
      await user.click(within(card).getByTestId('counter-card-header'));
    }
  };

  it('completes the delivery for the card\'s order and removes only that card, keeping the other expanded', async () => {
    const user = userEvent.setup();
    const completeDelivery = vi.spyOn(counterApi, 'completeDelivery').mockResolvedValue({
      orderId: order104.orderId,
      ticketNumber: 104,
      status: 'COMPLETED',
      completedAt: '2026-11-15T19:40:00Z',
      message: 'Entrega completada exitosamente.',
    });
    renderPage();
    await expandBoth(user);

    const card104 = screen.getByTestId(`counter-ticket-card-${order104.ticketId}`);
    await user.click(within(card104).getByTestId('counter-card-delivery-button'));

    await waitFor(() => {
      expect(screen.queryByTestId(`counter-ticket-card-${order104.ticketId}`)).not.toBeInTheDocument();
    });
    expect(completeDelivery).toHaveBeenCalledWith(order104.orderId);
    const card105 = screen.getByTestId(`counter-ticket-card-${order105.ticketId}`);
    expect(within(card105).getByTestId('counter-card-expanded-body')).toBeInTheDocument();
    expect(screen.getByTestId('counter-ready-count')).toHaveTextContent('1');
  });

  it('keeps the card on the board when the delivery request fails, so the operator can retry (RF-018)', async () => {
    const user = userEvent.setup();
    vi.spyOn(counterApi, 'completeDelivery').mockRejectedValue(new Error('Network Error'));
    renderPage();
    await expandBoth(user);

    const card104 = screen.getByTestId(`counter-ticket-card-${order104.ticketId}`);
    await user.click(within(card104).getByTestId('counter-card-delivery-button'));

    await waitFor(() => {
      expect(counterApi.completeDelivery).toHaveBeenCalledTimes(1);
    });
    expect(screen.getByTestId(`counter-ticket-card-${order104.ticketId}`)).toBeInTheDocument();
    expect(within(card104).getByTestId('counter-card-expanded-body')).toBeInTheDocument();
    expect(screen.getByTestId('counter-ready-count')).toHaveTextContent('2');
  });
});
