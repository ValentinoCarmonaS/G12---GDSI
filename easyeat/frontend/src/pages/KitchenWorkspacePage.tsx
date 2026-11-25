import React, { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { authApi } from '../api/authApi';
import { chefReviewFeedbackApi } from '../api/chefReviewFeedbackApi';
import { fetchDashboardSummary, startTicketPreparation } from '../api/kitchenKdsApi';
import { orderTrackingApi } from '../api/orderTrackingApi';
import { DishRejectionFeedbackBanner } from '../components/chef/DishRejectionFeedbackBanner';
import { KitchenKanbanBoard } from '../components/kds/KitchenKanbanBoard';
import { FeedbackState } from '../components/primitives/FeedbackState';
import { useKdsQueuePolling } from '../hooks/useKdsQueuePolling';
import type { ChefReviewFeedbackEntry } from '../types/dishReview';
import type { KdsDashboardSummaryResponse } from '../types/kds';

export const KitchenWorkspacePage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const returnedDishId = searchParams.get('dishId');
  const [feedbackEntries, setFeedbackEntries] = useState<ChefReviewFeedbackEntry[]>([]);

  // KDS active queue polling for Kanban board
  const { tickets, isLoading, error: pollingError, refetch: refetchQueue } = useKdsQueuePolling();
  const [dispatchedTicketIds, setDispatchedTicketIds] = useState<Set<string>>(new Set());
  const [actionError, setActionError] = useState<string | null>(null);

  // KDS live dashboard metrics (T027, Contract 07)
  const [kdsSummary, setKdsSummary] = useState<KdsDashboardSummaryResponse>({
    queuedOrdersCount: 0,
    inPreparationOrdersCount: 0,
    readyForPickupOrdersCount: 0,
    completedTodayCount: 0,
  });

  useEffect(() => {
    authApi.me().catch(() => { /* handled by interceptor */ });
  }, []);

  useEffect(() => {
    if (!returnedDishId) {
      setFeedbackEntries([]);
      return;
    }
    chefReviewFeedbackApi.getFeedback(returnedDishId)
      .then((feedback) => setFeedbackEntries(feedback.entries))
      .catch(() => setFeedbackEntries([]));
  }, [returnedDishId]);

  const loadKdsSummary = useCallback(async () => {
    try {
      const summary = await fetchDashboardSummary();
      if (summary) {
        setKdsSummary(summary);
      }
    } catch {
      // non-blocking fallback
    }
  }, []);

  useEffect(() => {
    void loadKdsSummary();

    const interval = setInterval(() => {
      if (document.visibilityState === 'visible') {
        void loadKdsSummary();
      }
    }, 4000);

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        void loadKdsSummary();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      clearInterval(interval);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [loadKdsSummary]);

  useEffect(() => {
    setDispatchedTicketIds((prev) => {
      if (prev.size === 0) return prev;
      const activeIds = new Set<string>();
      for (const t of tickets) {
        activeIds.add(t.ticketId);
        activeIds.add(t.orderId);
      }
      let changed = false;
      const next = new Set<string>();
      for (const id of prev) {
        if (activeIds.has(id)) {
          next.add(id);
        } else {
          changed = true;
        }
      }
      return changed ? next : prev;
    });
  }, [tickets]);

  const handleStartPreparation = async (ticketId: string) => {
    setActionError(null);
    try {
      await startTicketPreparation(ticketId);
      setKdsSummary((prev) => ({
        ...prev,
        queuedOrdersCount: Math.max(0, prev.queuedOrdersCount - 1),
        inPreparationOrdersCount: prev.inPreparationOrdersCount + 1,
      }));
      await refetchQueue();
      void loadKdsSummary();
    } catch (err: any) {
      console.error('Error starting ticket preparation:', err);
      setActionError('No se pudo iniciar la preparación de la comanda. Intentá nuevamente.');
    }
  };

  const handleReadyForPickup = async (ticketId: string) => {
    setActionError(null);
    setDispatchedTicketIds((prev) => new Set(prev).add(ticketId));
    try {
      await orderTrackingApi.markTicketReadyForPickup(ticketId);
      setKdsSummary((prev) => ({
        ...prev,
        inPreparationOrdersCount: Math.max(0, prev.inPreparationOrdersCount - 1),
        readyForPickupOrdersCount: prev.readyForPickupOrdersCount + 1,
      }));
      await refetchQueue();
      void loadKdsSummary();
    } catch (err: any) {
      console.error('Error marking ticket ready for pickup:', err);
      setDispatchedTicketIds((prev) => {
        const next = new Set(prev);
        next.delete(ticketId);
        return next;
      });
      setActionError('No se pudo despachar la comanda como lista. La tarjeta fue restituida.');
    }
  };

  const visibleTickets = tickets.filter(
    (ticket) =>
      !dispatchedTicketIds.has(ticket.ticketId) &&
      !dispatchedTicketIds.has(ticket.orderId)
  );

  return (
    <WorkspaceLayout
      title="Kitchen Display System (KDS)"
      subtitle="Comandas en preparación, demoras y despachos de cocina"
      fullWidth
    >
      {feedbackEntries.length > 0 && (
        <div className="mb-6">
          <DishRejectionFeedbackBanner entries={feedbackEntries} />
        </div>
      )}

      {/* Tira ultracompacta de 4 KPIs arriba */}
      <div
        data-testid="kds-kpi-strip"
        className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-6"
      >
        <div className="bg-ee-surface border-2 border-ee-text rounded-sm px-4 py-3 shadow-ee-elevated flex items-center justify-between gap-2">
          <span className="text-xs font-bold uppercase tracking-wider text-ee-text-muted">En Cola</span>
          <span className="text-xl font-black font-mono text-ee-text">{kdsSummary.queuedOrdersCount}</span>
        </div>
        <div className="bg-ee-surface border-2 border-ee-text rounded-sm px-4 py-3 shadow-ee-elevated flex items-center justify-between gap-2">
          <span className="text-xs font-bold uppercase tracking-wider text-ee-text-muted">En Preparación</span>
          <span className="text-xl font-black font-mono text-ee-text">{kdsSummary.inPreparationOrdersCount}</span>
        </div>
        <div className="bg-ee-surface border-2 border-ee-text rounded-sm px-4 py-3 shadow-ee-elevated flex items-center justify-between gap-2">
          <span className="text-xs font-bold uppercase tracking-wider text-ee-text-muted">Para Retirar</span>
          <span className="text-xl font-black font-mono text-ee-text">{kdsSummary.readyForPickupOrdersCount}</span>
        </div>
        <div className="bg-ee-surface border-2 border-ee-text rounded-sm px-4 py-3 shadow-ee-elevated flex items-center justify-between gap-2">
          <span className="text-xs font-bold uppercase tracking-wider text-ee-text-muted">Completadas hoy</span>
          <span className="text-xl font-black font-mono text-ee-text">{kdsSummary.completedTodayCount}</span>
        </div>
      </div>

      {/* Errores de sondeo o acción inline */}
      {pollingError && (
        <div className="mb-4">
          <FeedbackState
            variant="danger"
            title="Error de conexión con cocina"
            message="No se pudo actualizar el tablero KDS. Las comandas visibles se mantienen en pantalla."
            actionLabel="Reintentar"
            onAction={() => refetchQueue()}
          />
        </div>
      )}

      {actionError && (
        <div
          role="alert"
          aria-live="assertive"
          className="mb-4 bg-ee-danger-bg text-ee-danger-text border-2 border-ee-danger-border rounded-sm px-4 py-3 font-bold text-sm shadow-ee-elevated"
        >
          {actionError}
        </div>
      )}

      {/* Main KDS Kanban Board */}
      <div className="mb-8" data-testid="kds-kanban-section">
        <KitchenKanbanBoard
          tickets={visibleTickets}
          isLoading={isLoading}
          onStartPreparation={handleStartPreparation}
          onReadyForPickup={handleReadyForPickup}
          onDelayUpdated={() => {
            void refetchQueue();
            void loadKdsSummary();
          }}
          onDelayError={(msg) => setActionError(msg)}
        />
      </div>
    </WorkspaceLayout>
  );
};
