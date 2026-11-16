import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { authApi } from '../api/authApi';
import { chefReviewFeedbackApi } from '../api/chefReviewFeedbackApi';
import { fetchDashboardSummary, startTicketPreparation } from '../api/kitchenKdsApi';
import { DishRejectionFeedbackBanner } from '../components/chef/DishRejectionFeedbackBanner';
import { KitchenKanbanBoard } from '../components/kds/KitchenKanbanBoard';
import { useKdsQueuePolling } from '../hooks/useKdsQueuePolling';
import type { ChefReviewFeedbackEntry } from '../types/dishReview';
import type { KdsDashboardSummaryResponse } from '../types/kds';

export const KitchenWorkspacePage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const returnedDishId = searchParams.get('dishId');
  const [feedbackEntries, setFeedbackEntries] = useState<ChefReviewFeedbackEntry[]>([]);

  // KDS active queue polling for Kanban board
  const { tickets, refetch: refetchQueue } = useKdsQueuePolling();

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

  useEffect(() => {
    const loadKdsSummary = async () => {
      try {
        const summary = await fetchDashboardSummary();
        setKdsSummary(summary);
      } catch (err) {
        // non-blocking fallback
      }
    };

    loadKdsSummary();
    const interval = setInterval(loadKdsSummary, 10000);
    return () => clearInterval(interval);
  }, []);

  const handleStartPreparation = async (ticketId: string) => {
    await startTicketPreparation(ticketId);
    await refetchQueue();
  };

  return (
    <WorkspaceLayout
      title="Kitchen Display System (KDS)"
      subtitle="Comandas en preparación, demoras y despachos de cocina"
    >
      {feedbackEntries.length > 0 && (
        <div className="mb-6">
          <DishRejectionFeedbackBanner entries={feedbackEntries} />
        </div>
      )}

      {/* Main KDS Kanban Board (T025, CHF-3.2.1, COM-7.3.2 BIS) */}
      <div className="mb-8" data-testid="kds-kanban-section">
        <KitchenKanbanBoard
          tickets={tickets}
          onStartPreparation={handleStartPreparation}
          onDelayUpdated={() => refetchQueue()}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 mb-6">
        <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200 flex flex-col justify-between gap-3">
          <div>
            <h4 className="text-sm font-semibold text-gray-900">Catálogo de Ingredientes y Materias Primas</h4>
            <p className="text-xs text-gray-500 mt-0.5">
              Consultá la disponibilidad de stock de insumos en tiempo real para la elaboración de platos.
            </p>
          </div>
          <Link
            to="/cocina/ingredientes"
            className="inline-flex items-center justify-center px-4 py-2 bg-orange-600 hover:bg-orange-700 text-white text-xs font-semibold rounded-md shadow-sm transition-colors whitespace-nowrap self-start"
          >
            Ver Ingredientes →
          </Link>
        </div>
      </div>

      {/* KDS 4 Status Cards (T027, Contract 07) */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-amber-500 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-amber-600">En Cola</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">{kdsSummary.queuedOrdersCount} comandas</p>
        </div>

        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-blue-500 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-blue-600">En Preparación</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">{kdsSummary.inPreparationOrdersCount} comandas</p>
        </div>

        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-green-500 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-green-600">Para Retirar</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">{kdsSummary.readyForPickupOrdersCount} comandas</p>
        </div>

        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-gray-400 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-gray-600">Completadas hoy</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">{kdsSummary.completedTodayCount}</p>
        </div>
      </div>
    </WorkspaceLayout>
  );
};

export default KitchenWorkspacePage;
