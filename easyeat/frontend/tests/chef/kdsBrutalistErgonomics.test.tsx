import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { KitchenWorkspacePage } from '../../src/pages/KitchenWorkspacePage';
import { KitchenKanbanBoard } from '../../src/components/kds/KitchenKanbanBoard';
import { authApi } from '../../src/api/authApi';
import { fetchDashboardSummary, startTicketPreparation, addTicketDelay } from '../../src/api/kitchenKdsApi';
import { orderTrackingApi } from '../../src/api/orderTrackingApi';
import { useKdsQueuePolling } from '../../src/hooks/useKdsQueuePolling';
import type { KdsTicket } from '../../src/types/kds';

vi.mock('../../src/api/authApi', () => ({
  authApi: {
    me: vi.fn().mockResolvedValue({ data: { role: 'CHEF', fullName: 'Chef Test' } }),
  },
}));

vi.mock('../../src/api/kitchenKdsApi', () => ({
  fetchDashboardSummary: vi.fn(),
  startTicketPreparation: vi.fn(),
  addTicketDelay: vi.fn(),
}));

vi.mock('../../src/api/orderTrackingApi', () => ({
  orderTrackingApi: {
    markTicketReadyForPickup: vi.fn(),
  },
}));

vi.mock('../../src/hooks/useKdsQueuePolling', () => ({
  useKdsQueuePolling: vi.fn(),
}));

vi.mock('../../src/components/common/WorkspaceLayout', () => ({
  WorkspaceLayout: ({
    title,
    subtitle,
    fullWidth,
    children,
  }: {
    title: string;
    subtitle?: string;
    fullWidth?: boolean;
    children: React.ReactNode;
  }) => (
    <div data-testid="workspace-layout" data-full-width={fullWidth ? 'true' : 'false'}>
      <h1>{title}</h1>
      {subtitle && <p>{subtitle}</p>}
      {children}
    </div>
  ),
}));

const now = Date.now();
const recentStartedAt = new Date(now - 5 * 60 * 1000).toISOString();
const delayedStartedAt = new Date(now - 18 * 60 * 1000).toISOString();

const mockQueuedTicket: KdsTicket = {
  ticketId: 't-q1',
  ticketNumber: 101,
  orderId: 'o-101',
  status: 'QUEUED',
  fulfillmentMode: 'IMMEDIATE',
  pickupDate: '2026-11-23',
  timeSlotStart: '12:00:00',
  timeSlotEnd: '12:15:00',
  queuedAt: recentStartedAt,
  startedAt: null,
  items: [{ dishId: 'd-1', dishName: 'Milanesa Napolitana', quantity: 2 }],
};

const mockInPrepTicket: KdsTicket = {
  ticketId: 't-p1',
  ticketNumber: 102,
  orderId: 'o-102',
  status: 'IN_PREPARATION',
  fulfillmentMode: 'SCHEDULED',
  pickupDate: '2026-11-23',
  timeSlotStart: '12:15:00',
  timeSlotEnd: '12:30:00',
  queuedAt: recentStartedAt,
  startedAt: recentStartedAt,
  specificDelayMinutes: 0,
  items: [{ dishId: 'd-2', dishName: 'Risotto de Hongos', quantity: 1 }],
};

const mockDelayedTicket: KdsTicket = {
  ticketId: 't-p2',
  ticketNumber: 103,
  orderId: 'o-103',
  status: 'IN_PREPARATION',
  fulfillmentMode: 'IMMEDIATE',
  pickupDate: '2026-11-23',
  timeSlotStart: '11:45:00',
  timeSlotEnd: '12:00:00',
  queuedAt: delayedStartedAt,
  startedAt: delayedStartedAt,
  specificDelayMinutes: 5,
  items: [{ dishId: 'd-3', dishName: 'Bife de Chorizo', quantity: 1 }],
};

describe('Spec 027 US2 — KDS Brutalist Ergonomics & Resilience', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (fetchDashboardSummary as any).mockResolvedValue({
      queuedOrdersCount: 3,
      inPreparationOrdersCount: 2,
      readyForPickupOrdersCount: 1,
      completedTodayCount: 14,
    });
    (useKdsQueuePolling as any).mockReturnValue({
      tickets: [mockQueuedTicket, mockInPrepTicket],
      isLoading: false,
      error: null,
      refetch: vi.fn().mockResolvedValue(undefined),
    });
  });

  it('1) /cocina uses fullWidth, removes redundant ingredients block, renders top compact 4 KPI strip in JetBrains Mono without border-l-4 or slate-900, and shows LoadingSkeleton during initial load', () => {
    (useKdsQueuePolling as any).mockReturnValue({
      tickets: [],
      isLoading: true,
      error: null,
      refetch: vi.fn(),
    });

    const { container, rerender } = render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    expect(screen.getByTestId('workspace-layout')).toHaveAttribute('data-full-width', 'true');
    expect(screen.queryByText(/Catálogo de Ingredientes y Materias Primas/i)).not.toBeInTheDocument();
    expect(screen.getByTestId('kds-loading-skeleton')).toBeInTheDocument();

    (useKdsQueuePolling as any).mockReturnValue({
      tickets: [mockQueuedTicket, mockInPrepTicket],
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    rerender(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    const kpiStrip = screen.getByTestId('kds-kpi-strip');
    expect(within(kpiStrip).getByText('En Cola')).toBeInTheDocument();
    expect(within(kpiStrip).getByText('En Preparación')).toBeInTheDocument();
    expect(within(kpiStrip).getByText('Para Retirar')).toBeInTheDocument();
    expect(within(kpiStrip).getByText('Completadas hoy')).toBeInTheDocument();
    expect(kpiStrip.innerHTML).toContain('font-mono');
    expect(container.innerHTML).not.toContain('border-l-4');
    expect(container.innerHTML).not.toContain('bg-slate-900');
  });

  it('2) mobile quick switcher (>= 56px) toggles between Pendientes and En preparación keeping both column testids in DOM', () => {
    render(
      <KitchenKanbanBoard
        tickets={[mockQueuedTicket, mockInPrepTicket]}
        onStartPreparation={vi.fn()}
        onReadyForPickup={vi.fn()}
      />
    );

    const switcher = screen.getByTestId('kds-mobile-switcher');
    const pendingTab = within(switcher).getByRole('button', { name: /Pendientes/i });
    const inPrepTab = within(switcher).getByRole('button', { name: /En preparación/i });

    expect(pendingTab.className).toContain('min-h-[56px]');
    expect(inPrepTab.className).toContain('min-h-[56px]');
    expect(pendingTab).toHaveAttribute('aria-pressed', 'true');
    expect(inPrepTab).toHaveAttribute('aria-pressed', 'false');

    fireEvent.click(inPrepTab);
    expect(inPrepTab).toHaveAttribute('aria-pressed', 'true');
    expect(pendingTab).toHaveAttribute('aria-pressed', 'false');

    expect(screen.getByTestId('queued-column')).toBeInTheDocument();
    expect(screen.getByTestId('in-preparation-column')).toBeInTheDocument();
    expect(screen.getByTestId('kds-column-in-preparation')).toBeInTheDocument();
  });

  it('3) 3-block hard cards with #Ticket XXL, fulfillment badge, flex-wrap header, +5 (>= 56x56) separated from EMPEZAR/LISTO (>= 64px), DESPACHANDO... loading state, 1-tap LISTO without modal, and zero dish checkboxes', async () => {
    let resolveReady!: () => void;
    const readyPromise = new Promise<void>((res) => {
      resolveReady = res;
    });

    render(
      <KitchenKanbanBoard
        tickets={[mockQueuedTicket, mockInPrepTicket]}
        onStartPreparation={vi.fn()}
        onReadyForPickup={vi.fn(() => readyPromise)}
      />
    );

    const queuedCard = screen.getByTestId('queued-card-t-q1');
    expect(within(queuedCard).getByText('#101')).toBeInTheDocument();
    expect(within(queuedCard).getByText('Inmediato')).toBeInTheDocument();
    const startBtn = within(queuedCard).getByRole('button', { name: /EMPEZAR/i });
    expect(startBtn.className).toContain('min-h-[64px]');

    const prepCard = screen.getByTestId('in-preparation-card-t-p1');
    expect(within(prepCard).getByText('#102')).toBeInTheDocument();
    expect(within(prepCard).getByText('Programado')).toBeInTheDocument();
    expect(within(prepCard).queryAllByRole('checkbox')).toHaveLength(0);

    const delayBtn = within(prepCard).getByRole('button', { name: '+5' });
    expect(delayBtn.className).toContain('min-h-[56px]');
    expect(delayBtn.className).toContain('min-w-[56px]');

    const readyBtn = within(prepCard).getByRole('button', { name: /LISTO/i });
    expect(readyBtn.className).toContain('min-h-[64px]');

    fireEvent.click(readyBtn);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(within(prepCard).getByRole('button', { name: /DESPACHANDO\.\.\./i })).toBeDisabled();

    resolveReady();
    await waitFor(() => {
      expect(within(prepCard).getByRole('button', { name: /LISTO/i })).not.toBeDisabled();
    });
  });

  it('4) >= 15 min delay alert uses border-4, data-delayed="true", DEMORA badge, and neutral dish text (text-ee-text)', () => {
    render(
      <KitchenKanbanBoard
        tickets={[mockDelayedTicket]}
        onStartPreparation={vi.fn()}
        onReadyForPickup={vi.fn()}
      />
    );

    const delayedCard = screen.getByTestId('in-preparation-card-t-p2');
    expect(delayedCard).toHaveAttribute('data-delayed', 'true');
    expect(delayedCard.className).toContain('border-4');
    expect(within(delayedCard).getByText(/DEMORA/i)).toBeInTheDocument();

    const dishEl = within(delayedCard).getByText(/Bife de Chorizo/i);
    expect(dishEl.className).toContain('text-ee-text');
  });

  it('5) polling error shows FeedbackState with Reintentar keeping tickets visible, and EMPEZAR/LISTO/+5 errors show role="alert" aria-live and restore dispatched card', async () => {
    const refetchMock = vi.fn().mockResolvedValue(undefined);
    (useKdsQueuePolling as any).mockReturnValue({
      tickets: [mockQueuedTicket, mockInPrepTicket],
      isLoading: false,
      error: new Error('Network error'),
      refetch: refetchMock,
    });
    (startTicketPreparation as any).mockRejectedValueOnce(new Error('Start failed'));
    (orderTrackingApi.markTicketReadyForPickup as any).mockRejectedValueOnce(new Error('Ready failed'));
    (addTicketDelay as any).mockRejectedValueOnce(new Error('Delay failed'));

    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    // Polling error banner + tickets still visible
    expect(screen.getByText(/#101/)).toBeInTheDocument();
    const retryBtn = screen.getByRole('button', { name: /Reintentar/i });
    fireEvent.click(retryBtn);
    expect(refetchMock).toHaveBeenCalled();

    // EMPEZAR error
    fireEvent.click(screen.getByRole('button', { name: /EMPEZAR/i }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/No se pudo iniciar/i);
    });

    // LISTO error restores card
    fireEvent.click(screen.getByRole('button', { name: /LISTO/i }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/No se pudo despachar/i);
    });
    expect(screen.getByTestId('in-preparation-card-t-p1')).toBeInTheDocument();

    // +5 error
    fireEvent.click(screen.getByRole('button', { name: '+5' }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/No se pudo sumar demora/i);
    });
  });
});
