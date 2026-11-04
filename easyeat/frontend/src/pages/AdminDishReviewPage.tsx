import React, { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
import { ReviewInboxTabs } from '@/components/dishreview/ReviewInboxTabs';
import { ReviewInboxList } from '@/components/dishreview/ReviewInboxList';
import { InboxPagination } from '@/components/dishreview/InboxPagination';
import { DishAuditPanel } from '@/components/dishreview/DishAuditPanel';
import { DishDecisionActions } from '@/components/dishreview/DishDecisionActions';
import { useDishReviewInbox } from '@/hooks/useDishReviewInbox';
import { dishReviewApi } from '@/api/dishReviewApi';
import type { DishReviewDetail, InboxTab } from '@/types/dishReview';

/** FR-032: a detail failure is described by what the Administrator can do about it. */
function describeDetailError(err: unknown): string {
  const status = (err as { response?: { status?: number } })?.response?.status;
  if (status === 404) {
    return 'No se encontró el plato. Puede haber sido eliminado por el Chef mientras mirabas la bandeja.';
  }
  if (status === 403) {
    return 'No tenés permiso para ver la ficha de este plato.';
  }
  return 'No se pudo cargar la ficha del plato. Probá abrirla de nuevo.';
}

/**
 * `/admin/dishes` — the review inbox beside the culinary file (FR-002, FR-006).
 *
 * The inbox stays on screen while a dish is open, so the Administrator keeps sight of the queue
 * they are working through. Two rules keep the two halves honest about each other: switching tabs
 * closes the open dish, because it belonged to the previous view; and a failed detail load clears
 * the panel rather than leaving the previous dish visible beside an error, which would otherwise
 * have the Administrator reading one file while the application talks about another.
 */
export const AdminDishReviewPage: React.FC = () => {
  const inbox = useDishReviewInbox();

  const [detail, setDetail] = useState<DishReviewDetail | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const openDish = useCallback(async (dishId: string) => {
    setIsDetailLoading(true);
    // Cleared before the request, not after it resolves: the panel must never show one dish
    // while the page is loading another.
    setDetail(null);
    setDetailError(null);
    try {
      setDetail(await dishReviewApi.getDetail(dishId));
    } catch (err) {
      setDetailError(describeDetailError(err));
    } finally {
      setIsDetailLoading(false);
    }
  }, []);

  const changeTab = useCallback(
    (tab: InboxTab) => {
      if (inbox.tab !== tab) {
        inbox.setTab(tab);
        // The open dish belonged to the view being left behind.
        setDetail(null);
        setDetailError(null);
      }
    },
    [inbox]
  );

  /**
   * Re-fetches the open dish's detail and the inbox after a decision changed either one:
   * a price save can flip `publicationGate`, an approval publishes the dish, a rejection returns
   * it to `DRAFT` and moves it out of the Pending tab. Re-running `openDish` keeps this on the
   * same path as every other detail load, including the loading state and FR-032's error
   * handling if the refetch itself fails.
   */
  const handleDecided = useCallback(() => {
    if (detail) {
      void openDish(detail.dishId);
    }
    inbox.refresh();
  }, [detail, openDish, inbox]);

  const handleCancelled = useCallback(() => {
    setDetail(null);
    setDetailError(null);
    setIsDetailLoading(false);
    inbox.refresh();
  }, [inbox]);

  return (
    <WorkspaceLayout
      title="Bandeja de Revisión de Platos"
      subtitle="Platos enviados por cocina, con su ficha técnica y su auditoría de Food Cost"
    >
      <div className="mb-4">
        <Link
          to="/admin"
          className="text-sm font-medium text-purple-600 hover:text-purple-700 transition"
        >
          ← Volver al Panel de Administración
        </Link>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <div className="lg:col-span-2 bg-white rounded-lg border border-gray-200 overflow-hidden">
          <ReviewInboxTabs
            activeTab={inbox.tab}
            pendingCount={inbox.pendingCount}
            onTabChange={changeTab}
          />
          <ReviewInboxList
            items={inbox.items}
            tab={inbox.tab}
            isLoading={inbox.isLoading}
            error={inbox.error}
            onSelect={openDish}
            onRetry={inbox.refresh}
          />
          <InboxPagination
            page={inbox.page}
            totalPages={inbox.totalPages}
            hasNext={inbox.hasNext}
            onPageChange={inbox.setPage}
          />
        </div>

        <div className="lg:col-span-3 bg-white rounded-lg border border-gray-200 p-6">
          {isDetailLoading && (
            <div data-testid="detail-loading" aria-busy="true" className="space-y-3 animate-pulse">
              <span className="sr-only">Cargando la ficha del plato…</span>
              <div className="h-6 w-1/2 bg-gray-200 rounded" />
              <div className="h-4 w-1/3 bg-gray-100 rounded" />
              <div className="h-32 bg-gray-100 rounded" />
            </div>
          )}

          {!isDetailLoading && detailError && (
            <div
              data-testid="detail-error"
              role="alert"
              className="px-4 py-8 text-center border border-red-200 bg-red-50 rounded"
            >
              <p className="text-sm text-red-800">{detailError}</p>
            </div>
          )}

          {!isDetailLoading && !detailError && detail && (
            <div className="space-y-6">
              <DishAuditPanel detail={detail} />
              <DishDecisionActions
                detail={detail}
                onDecided={handleDecided}
                onCancelled={handleCancelled}
              />
            </div>
          )}

          {!isDetailLoading && !detailError && !detail && (
            <div data-testid="detail-placeholder" className="px-4 py-16 text-center">
              <p className="font-medium text-gray-900">Elegí un plato de la bandeja</p>
              <p className="mt-1 text-sm text-gray-500 max-w-sm mx-auto">
                Acá vas a ver su ficha técnica, el desglose del Food Cost y las condiciones que le
                faltan para publicarse.
              </p>
            </div>
          )}
        </div>
      </div>
    </WorkspaceLayout>
  );
};

export default AdminDishReviewPage;
