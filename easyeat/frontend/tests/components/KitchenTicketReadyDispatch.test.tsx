import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { KitchenWorkspacePage } from '@/pages/KitchenWorkspacePage';
import { authApi } from '@/api/authApi';
import { chefReviewFeedbackApi } from '@/api/chefReviewFeedbackApi';
import * as kitchenKdsApi from '@/api/kitchenKdsApi';
import { orderTrackingApi } from '@/api/orderTrackingApi';
import * as AuthModule from '@/hooks/useAuth';

vi.mock('@/hooks/useAuth');
vi.mock('@/api/chefReviewFeedbackApi', () => ({
  chefReviewFeedbackApi: { getFeedback: vi.fn() },
}));

describe('KitchenTicketReadyDispatch (T010, US1, CHF-4.1.3, RF-001..RF-004, Invariantes 1..4)', () => {
  const sampleInPrepTickets: kitchenKdsApi.KitchenOrderPageResponse = {
    content: [
      {
        ticketId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
        ticketNumber: 104,
        orderId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
        status: 'IN_PREPARATION',
        fulfillmentMode: 'IMMEDIATE',
        pickupDate: '2026-11-15',
        timeSlotStart: '13:00',
        timeSlotEnd: '13:15',
        queuedAt: '2026-11-15T19:10:00Z',
        items: [
          {
            dishId: 'dish-1',
            dishName: 'Hamburguesa Completa con Papas',
            quantity: 2,
          },
          {
            dishId: 'dish-2',
            dishName: 'Limonada con Menta y Jengibre',
            quantity: 1,
          },
        ],
      },
      {
        ticketId: 'a15c329b-81d3-4e89-b620-7f2cb14197e2',
        ticketNumber: 105,
        orderId: '50e932b1-12c8-4ec7-9104-58a47de0281b',
        status: 'IN_PREPARATION',
        fulfillmentMode: 'SCHEDULED',
        pickupDate: '2026-11-15',
        timeSlotStart: '13:15',
        timeSlotEnd: '13:30',
        queuedAt: '2026-11-15T19:12:00Z',
        items: [
          {
            dishId: 'dish-3',
            dishName: 'Ensalada Caesar con Pollo',
            quantity: 1,
          },
        ],
      },
    ],
    pageable: {
      pageNumber: 0,
      pageSize: 20,
    },
    totalElements: 2,
    totalPages: 1,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.mocked(chefReviewFeedbackApi.getFeedback).mockResolvedValue({
      dishId: 'dish-1',
      entries: [],
    });
    vi.spyOn(kitchenKdsApi, 'fetchDashboardSummary').mockResolvedValue({
      queuedOrdersCount: 1,
      inPreparationOrdersCount: 2,
      readyForPickupOrdersCount: 0,
      completedTodayCount: 5,
    });
    vi.spyOn(kitchenKdsApi, 'fetchKitchenOrders').mockResolvedValue(sampleInPrepTickets);
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-user-1',
        email: 'carlos.chef@comedorcentral.com',
        role: 'CHEF',
        tenantId: 'tenant-comedor-1',
        tokenVersion: 1,
      },
      token: 'jwt-chef-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('RF-001 / AC-1 / Invariante 3: renders active tickets in Column 2 with prominent "LISTO" button and no dish checkboxes', async () => {
    const { container } = render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    const col2 = await screen.findByTestId('kds-column-in-preparation');
    expect(col2).toBeInTheDocument();

    const card104 = await within(col2).findByTestId(
      'kds-ticket-card-9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d'
    );
    expect(within(card104).getByText('#104')).toBeInTheDocument();
    expect(within(card104).getByText(/13:00 - 13:15/)).toBeInTheDocument();
    expect(within(card104).getByText('2x Hamburguesa Completa con Papas')).toBeInTheDocument();
    expect(within(card104).getByText('1x Limonada con Menta y Jengibre')).toBeInTheDocument();

    const readyBtn = within(card104).getByRole('button', { name: 'LISTO' });
    expect(readyBtn).toBeInTheDocument();

    // No per-dish checklist checkboxes in Column 2 cards (Invariante 3, Caso borde 10)
    expect(container.querySelectorAll('input[type="checkbox"]')).toHaveLength(0);
  });

  it('RF-002 / RF-004 / AC-3: clicking "LISTO" calls markTicketReadyForPickup without confirmation modals and immediately removes the ticket from Column 2', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, 'confirm');
    const markReadySpy = vi
      .spyOn(orderTrackingApi, 'markTicketReadyForPickup')
      .mockResolvedValue({
        ticketId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
        orderId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
        ticketNumber: 104,
        status: 'READY_FOR_PICKUP',
        readyAt: '2026-11-15T19:30:00.123456Z',
      });

    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    const col2 = await screen.findByTestId('kds-column-in-preparation');
    const card104 = await within(col2).findByTestId(
      'kds-ticket-card-9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d'
    );
    const card105 = await within(col2).findByTestId(
      'kds-ticket-card-a15c329b-81d3-4e89-b620-7f2cb14197e2'
    );
    expect(card105).toBeInTheDocument();

    const readyBtn104 = within(card104).getByRole('button', { name: 'LISTO' });
    await user.click(readyBtn104);

    // No confirmation popup or dialog (RF-002, AC-1)
    expect(confirmSpy).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();

    expect(markReadySpy).toHaveBeenCalledTimes(1);
    expect(markReadySpy).toHaveBeenCalledWith('9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d');

    // Immediate removal of Ticket #104 from Column 2 while Ticket #105 remains (RF-004, AC-3, Invariante 4)
    await waitFor(() => {
      expect(
        screen.queryByTestId('kds-ticket-card-9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d')
      ).not.toBeInTheDocument();
    });
    expect(
      screen.getByTestId('kds-ticket-card-a15c329b-81d3-4e89-b620-7f2cb14197e2')
    ).toBeInTheDocument();
  });
});
