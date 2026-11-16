import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { CounterWorkspacePage } from '@/pages/CounterWorkspacePage';
import { authApi } from '@/api/authApi';
import * as AuthModule from '@/hooks/useAuth';
import * as CounterPollingModule from '@/hooks/useCounterOrdersPolling';
import type { CounterReadyOrder } from '@/types/counter';

vi.mock('@/hooks/useAuth');

const sampleOrders: CounterReadyOrder[] = [
  {
    ticketId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
    orderId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
    ticketNumber: 104,
    timeSlot: {
      start: '13:00',
      end: '13:15',
    },
    readyAt: '2026-11-15T19:30:00Z',
    customer: {
      userId: 'c71e2289-4b68-45e0-a7d1-e6e7d6928e01',
      fullName: 'Valentino Carmona',
      photoUrl: '/api/v1/profile/users/c71e2289-4b68-45e0-a7d1-e6e7d6928e01/avatar',
    },
    securityCode: '4821',
    items: [
      {
        quantity: 2,
        dishName: 'Hamburguesa Completa con Papas',
      },
      {
        quantity: 1,
        dishName: 'Limonada con Menta y Jengibre',
      },
    ],
  },
  {
    ticketId: 'a15c329b-81d3-4e89-b620-7f2cb14197e2',
    orderId: '50e932b1-12c8-4ec7-9104-58a47de0281b',
    ticketNumber: 105,
    timeSlot: {
      start: '13:15',
      end: '13:30',
    },
    readyAt: '2026-11-15T19:33:45Z',
    customer: {
      userId: 'd98f1214-9271-4899-8ab3-772c21980a34',
      fullName: 'Lucía Morales',
      photoUrl: null,
    },
    securityCode: '9102',
    items: [
      {
        quantity: 1,
        dishName: 'Ensalada Caesar con Pollo',
      },
    ],
  },
];

describe('CounterWorkspacePage (T021, T024, US3, ENC-2.1.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'user-enc-1',
        email: 'maria.encargada@laesquina.com',
        role: 'ENCARGADO',
        tenantId: 'tenant-enc-1',
        tokenVersion: 1,
      },
      token: 'jwt-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
    vi.spyOn(CounterPollingModule, 'useCounterOrdersPolling').mockReturnValue({
      orders: [],
      isLoading: false,
      isDisconnected: false,
      serverOffsetMs: 0,
      refresh: vi.fn(),
    });
  });

  it('renders workspace layout with counter title and invokes authApi.me on mount', () => {
    render(
      <MemoryRouter>
        <CounterWorkspacePage />
      </MemoryRouter>
    );

    expect(screen.getByText('Mostrador y Despacho')).toBeInTheDocument();
    expect(
      screen.getByText(
        'Punto de atención para gestión y entrega de pedidos en salón'
      )
    ).toBeInTheDocument();
    expect(authApi.me).toHaveBeenCalledTimes(1);
  });

  it('RF-019: renders neutral empty state message "No hay pedidos listos para entrega" when queue is empty', () => {
    render(
      <MemoryRouter>
        <CounterWorkspacePage />
      </MemoryRouter>
    );

    expect(
      screen.getByText('No hay pedidos listos para entrega')
    ).toBeInTheDocument();
  });

  it('RF-006 / RF-010 / RF-015: renders cards closed by default, supports multiple simultaneously expanded cards, and preserves expanded state across polling updates', async () => {
    const user = userEvent.setup();
    const pollingSpy = vi
      .spyOn(CounterPollingModule, 'useCounterOrdersPolling')
      .mockReturnValue({
        orders: sampleOrders,
        isLoading: false,
        isDisconnected: false,
        serverOffsetMs: 0,
        refresh: vi.fn(),
      });

    const { rerender } = render(
      <MemoryRouter>
        <CounterWorkspacePage />
      </MemoryRouter>
    );

    const card104 = screen.getByTestId(
      `counter-ticket-card-${sampleOrders[0].ticketId}`
    );
    const card105 = screen.getByTestId(
      `counter-ticket-card-${sampleOrders[1].ticketId}`
    );

    // Closed by default (Invariante 5)
    expect(
      within(card104).queryByTestId('counter-card-expanded-body')
    ).not.toBeInTheDocument();
    expect(
      within(card105).queryByTestId('counter-card-expanded-body')
    ).not.toBeInTheDocument();

    // Expand both cards simultaneously (RF-010, Invariante 6)
    await user.click(within(card104).getByTestId('counter-card-header'));
    await user.click(within(card105).getByTestId('counter-card-header'));

    expect(
      within(card104).getByTestId('counter-card-expanded-body')
    ).toBeInTheDocument();
    expect(
      within(card105).getByTestId('counter-card-expanded-body')
    ).toBeInTheDocument();

    // Simulate background polling refresh arriving with a new 3rd order (RF-015, RF-016, Invariante 7)
    const thirdOrder: CounterReadyOrder = {
      ...sampleOrders[0],
      ticketId: 'b22d440c-92e4-4f90-c731-8a3dc25208f3',
      orderId: '61fa43c2-23d9-4fd8-a215-69b58ef1392c',
      ticketNumber: 106,
      readyAt: '2026-11-15T19:34:30Z',
    };

    pollingSpy.mockReturnValue({
      orders: [...sampleOrders, thirdOrder],
      isLoading: false,
      isDisconnected: false,
      serverOffsetMs: 0,
      refresh: vi.fn(),
    });

    rerender(
      <MemoryRouter>
        <CounterWorkspacePage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(
        screen.getByTestId(`counter-ticket-card-${thirdOrder.ticketId}`)
      ).toBeInTheDocument();
    });

    // Cards 104 and 105 remain expanded while new Card 106 enters closed by default
    expect(
      within(
        screen.getByTestId(`counter-ticket-card-${sampleOrders[0].ticketId}`)
      ).getByTestId('counter-card-expanded-body')
    ).toBeInTheDocument();
    expect(
      within(
        screen.getByTestId(`counter-ticket-card-${sampleOrders[1].ticketId}`)
      ).getByTestId('counter-card-expanded-body')
    ).toBeInTheDocument();
    expect(
      within(
        screen.getByTestId(`counter-ticket-card-${thirdOrder.ticketId}`)
      ).queryByTestId('counter-card-expanded-body')
    ).not.toBeInTheDocument();
  });

  it('RF-018: displays non-blocking connection banner when isDisconnected is true without hiding visible orders', () => {
    vi.spyOn(
      CounterPollingModule,
      'useCounterOrdersPolling'
    ).mockReturnValue({
      orders: sampleOrders,
      isLoading: false,
      isDisconnected: true,
      serverOffsetMs: 0,
      refresh: vi.fn(),
    });

    render(
      <MemoryRouter>
        <CounterWorkspacePage />
      </MemoryRouter>
    );

    expect(
      screen.getByText('Sin conexión — Reintentando...')
    ).toBeInTheDocument();
    expect(screen.getByText('#104')).toBeInTheDocument();
    expect(screen.getByText('#105')).toBeInTheDocument();
  });
});
