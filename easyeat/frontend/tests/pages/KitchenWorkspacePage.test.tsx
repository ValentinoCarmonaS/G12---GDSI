import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { KitchenWorkspacePage } from '@/pages/KitchenWorkspacePage';
import { authApi } from '@/api/authApi';
import { chefReviewFeedbackApi } from '@/api/chefReviewFeedbackApi';
import * as kitchenKdsApi from '@/api/kitchenKdsApi';
import { orderTrackingApi } from '@/api/orderTrackingApi';
import * as AuthModule from '@/hooks/useAuth';
import type { ChefReviewFeedback } from '@/types/dishReview';
import type { KdsTicket } from '@/types/kds';

vi.mock('@/hooks/useAuth');
vi.mock('@/api/chefReviewFeedbackApi', () => ({
  chefReviewFeedbackApi: { getFeedback: vi.fn() },
}));

describe('KitchenWorkspacePage (T029, CHF-1.2.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.mocked(chefReviewFeedbackApi.getFeedback).mockResolvedValue({
      dishId: 'dish-1',
      entries: [],
    });
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

  it('renders kitchen workspace layout with KDS title and order status cards (T029)', () => {
    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    expect(screen.getByText(/kitchen display system/i)).toBeInTheDocument();
    expect(screen.getByText(/comandas en preparación.*despachos de cocina/i)).toBeInTheDocument();
    expect(screen.getByText('En Cola')).toBeInTheDocument();
    expect(screen.getByText('En Preparación')).toBeInTheDocument();
    expect(screen.getByText('Para Retirar')).toBeInTheDocument();
    expect(screen.getByText('Completadas hoy')).toBeInTheDocument();

    // T025: Verify KitchenKanbanBoard is embedded into KitchenWorkspacePage
    expect(screen.getByTestId('kds-kanban-section')).toBeInTheDocument();
  });

  it('invokes authApi.me on mount to validate token version and session liveness', () => {
    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    expect(authApi.me).toHaveBeenCalledTimes(1);
  });

  it('exposes dish management only through the header and keeps the KDS cards intact (C-08, FR-028)', () => {
    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    // The dish-management card is no longer nested inside the KDS view...
    expect(screen.queryByText(/gestión de platos y fichas técnicas/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /gestionar platos →/i })).not.toBeInTheDocument();

    // ...the section itself lives in the shared header...
    expect(screen.getByRole('link', { name: 'Gestionar Platos' })).toHaveAttribute(
      'href',
      '/cocina/platos'
    );

    // ...el bloque redundante de ingredientes fue retirado (FR-006 / FR-015(b)) mientras los 4 KPIs se preservan.
    expect(screen.queryByText(/catálogo de ingredientes y materias primas/i)).not.toBeInTheDocument();
    expect(screen.getByText('En Cola')).toBeInTheDocument();
    expect(screen.getByText('En Preparación')).toBeInTheDocument();
    expect(screen.getByText('Para Retirar')).toBeInTheDocument();
    expect(screen.getByText('Completadas hoy')).toBeInTheDocument();

    // T025: Verify KitchenKanbanBoard is embedded into KitchenWorkspacePage
    expect(screen.getByTestId('kds-kanban-section')).toBeInTheDocument();
  });

  describe('a draft returned by rejection (FR-020, FR-023)', () => {
    const feedback: ChefReviewFeedback = {
      dishId: 'dish-1',
      entries: [
        {
          cycleNumber: 1,
          reviewerName: 'Lucía Ferraro',
          comment: 'El costo del salmón dispara el food cost. Usá merluza y reenviá la ficha.',
          createdAt: '2026-10-29T10:00:00Z',
        },
      ],
    };

    it('renders the feedback banner when the URL names the returned draft', async () => {
      vi.mocked(chefReviewFeedbackApi.getFeedback).mockResolvedValue(feedback);

      render(
        <MemoryRouter initialEntries={['/cocina?dishId=dish-1']}>
          <KitchenWorkspacePage />
        </MemoryRouter>
      );

      await waitFor(() =>
        expect(screen.getByText(feedback.entries[0].comment as string)).toBeInTheDocument()
      );
      expect(screen.getByText(/Lucía Ferraro/)).toBeInTheDocument();
    });

    it('renders no banner when the URL names no draft', () => {
      render(
        <MemoryRouter>
          <KitchenWorkspacePage />
        </MemoryRouter>
      );

      expect(chefReviewFeedbackApi.getFeedback).not.toHaveBeenCalled();
      expect(screen.queryByTestId('rejection-feedback-banner')).not.toBeInTheDocument();
    });

    it('shows no acquisition cost, Food Cost or margin figure anywhere on the page', async () => {
      vi.mocked(chefReviewFeedbackApi.getFeedback).mockResolvedValue(feedback);

      const { container } = render(
        <MemoryRouter initialEntries={['/cocina?dishId=dish-1']}>
          <KitchenWorkspacePage />
        </MemoryRouter>
      );

      await waitFor(() =>
        expect(screen.getByText(feedback.entries[0].comment as string)).toBeInTheDocument()
      );

      // The feedback comment legitimately mentions "costo"/"food cost" in prose — that is the
      // Administrator's own words, not a financial field this page renders. The structural
      // guarantee is that no element carries a numeric financial value, which this page has no
      // way to render in the first place: it holds no dish price, margin or cost state at all.
      const amounts = container.querySelectorAll('[data-testid*="cost"], [data-testid*="price"], [data-testid*="margin"]');
      expect(amounts).toHaveLength(0);
    });
  });

  describe('Spec 028 US5 (T076): real-time KDS KPI synchronization', () => {
    const queuedTicket: KdsTicket = {
      ticketId: 'ticket-q-1',
      ticketNumber: 101,
      orderId: 'order-q-1',
      status: 'QUEUED',
      fulfillmentMode: 'IMMEDIATE',
      pickupDate: '2026-11-25',
      timeSlotStart: '12:00:00',
      timeSlotEnd: '12:15:00',
      queuedAt: '2026-11-25T15:00:00Z',
      startedAt: null,
      items: [{ dishId: 'd1', dishName: 'Milanesa', quantity: 1 }],
    };

    const inPrepTicket: KdsTicket = {
      ...queuedTicket,
      ticketId: 'ticket-p-2',
      ticketNumber: 102,
      orderId: 'order-p-2',
      status: 'IN_PREPARATION',
      startedAt: '2026-11-25T15:05:00Z',
    };

    afterEach(() => {
      vi.useRealTimers();
      vi.restoreAllMocks();
    });

    it('refreshes fetchDashboardSummary immediately after handleStartPreparation and handleReadyForPickup', async () => {
      const fetchOrdersSpy = vi.spyOn(kitchenKdsApi, 'fetchKitchenOrders').mockResolvedValue({
        content: [queuedTicket, inPrepTicket],
        pageable: { pageNumber: 0, pageSize: 100 },
        totalElements: 2,
        totalPages: 1,
      });
      const fetchSummarySpy = vi.spyOn(kitchenKdsApi, 'fetchDashboardSummary')
        .mockResolvedValueOnce({
          queuedOrdersCount: 1,
          inPreparationOrdersCount: 1,
          readyForPickupOrdersCount: 0,
          completedTodayCount: 0,
        })
        .mockResolvedValueOnce({
          queuedOrdersCount: 0,
          inPreparationOrdersCount: 2,
          readyForPickupOrdersCount: 0,
          completedTodayCount: 0,
        })
        .mockResolvedValueOnce({
          queuedOrdersCount: 0,
          inPreparationOrdersCount: 1,
          readyForPickupOrdersCount: 1,
          completedTodayCount: 0,
        });
      vi.spyOn(kitchenKdsApi, 'startTicketPreparation').mockResolvedValue({
        ticketId: queuedTicket.ticketId,
        ticketNumber: queuedTicket.ticketNumber,
        orderId: queuedTicket.orderId,
        status: 'IN_PREPARATION',
        startedAt: '2026-11-25T15:06:00Z',
      });
      vi.spyOn(orderTrackingApi, 'markTicketReadyForPickup').mockResolvedValue({} as any);

      render(
        <MemoryRouter>
          <KitchenWorkspacePage />
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(fetchSummarySpy).toHaveBeenCalledTimes(1);
        expect(fetchOrdersSpy).toHaveBeenCalledTimes(1);
      });

      const startBtn = await screen.findByRole('button', { name: /iniciar preparación/i });
      fireEvent.click(startBtn);

      await waitFor(() => {
        expect(fetchSummarySpy).toHaveBeenCalledTimes(2);
      });

      const readyBtn = await screen.findByRole('button', { name: /listo para retirar/i });
      fireEvent.click(readyBtn);

      await waitFor(() => {
        expect(fetchSummarySpy).toHaveBeenCalledTimes(3);
      });
    });

    it('refreshes fetchDashboardSummary on the 4-second active queue cadence and on visibilitychange', async () => {
      vi.useFakeTimers();
      vi.spyOn(kitchenKdsApi, 'fetchKitchenOrders').mockResolvedValue({
        content: [],
        pageable: { pageNumber: 0, pageSize: 100 },
        totalElements: 0,
        totalPages: 1,
      });
      const fetchSummarySpy = vi.spyOn(kitchenKdsApi, 'fetchDashboardSummary').mockResolvedValue({
        queuedOrdersCount: 0,
        inPreparationOrdersCount: 0,
        readyForPickupOrdersCount: 0,
        completedTodayCount: 1,
      });

      render(
        <MemoryRouter>
          <KitchenWorkspacePage />
        </MemoryRouter>
      );

      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      expect(fetchSummarySpy).toHaveBeenCalledTimes(1);

      // 4-second cadence (aligned with active queue polling, not 10s)
      await act(async () => {
        await vi.advanceTimersByTimeAsync(4100);
      });
      expect(fetchSummarySpy).toHaveBeenCalledTimes(2);

      // visibilitychange back to visible triggers immediate refresh
      Object.defineProperty(document, 'visibilityState', {
        configurable: true,
        value: 'visible',
      });
      await act(async () => {
        document.dispatchEvent(new Event('visibilitychange'));
        await Promise.resolve();
      });
      expect(fetchSummarySpy).toHaveBeenCalledTimes(3);
    });
  });
});
