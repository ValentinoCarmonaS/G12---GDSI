import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
import { GlassCard } from '@/components/admin/GlassCard';
import { Button, EmptyState, FeedbackState, LoadingSkeleton } from '@/components/primitives';
import { ReviewInboxTabs } from '@/components/dishreview/ReviewInboxTabs';
import { ReviewInboxList } from '@/components/dishreview/ReviewInboxList';
import { InboxPagination } from '@/components/dishreview/InboxPagination';
import { DishAuditPanel } from '@/components/dishreview/DishAuditPanel';
import { DishDecisionActions } from '@/components/dishreview/DishDecisionActions';
import { useDishReviewInbox } from '@/hooks/useDishReviewInbox';
import { useOptionalAdminPendingDishes } from '@/context/AdminPendingDishesContext';
import { dishReviewApi } from '@/api/dishReviewApi';
import type { DishReviewDetail, InboxTab } from '@/types/dishReview';

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

export const AdminDishReviewPage: React.FC = () => {
  const inbox = useDishReviewInbox();
  const pendingCtx = useOptionalAdminPendingDishes();

  const [selectedDishId, setSelectedDishId] = useState<string | null>(null);
  const [detail, setDetail] = useState<DishReviewDetail | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  // Keep global navigation pendingCount synchronized in memory with inbox data (no extra requests).
  useEffect(() => {
    if (pendingCtx && !inbox.isLoading && !inbox.error) {
      pendingCtx.setPendingCount(inbox.pendingCount);
    }
  }, [pendingCtx, inbox.isLoading, inbox.error, inbox.pendingCount]);

  const openDish = useCallback(async (dishId: string) => {
    setSelectedDishId(dishId);
    setIsDetailLoading(true);
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

  const closeDetailOnMobile = useCallback(() => {
    setSelectedDishId(null);
    setDetail(null);
    setDetailError(null);
    setIsDetailLoading(false);
  }, []);

  const changeTab = useCallback(
    (tab: InboxTab) => {
      if (inbox.tab !== tab) {
        inbox.setTab(tab);
        setSelectedDishId(null);
        setDetail(null);
        setDetailError(null);
      }
    },
    [inbox]
  );

  const handleDecided = useCallback(() => {
    if (detail) {
      void openDish(detail.dishId);
    }
    inbox.refresh();
  }, [detail, openDish, inbox]);

  const handleCancelled = useCallback(() => {
    pendingCtx?.decrementPendingCount();
    setSelectedDishId(null);
    setDetail(null);
    setDetailError(null);
    setIsDetailLoading(false);
    inbox.refresh();
  }, [inbox, pendingCtx]);

  const isDetailActive = Boolean(selectedDishId || detail || isDetailLoading || detailError);

  return (
    <WorkspaceLayout
      title="Bandeja de Revisión de Platos"
      subtitle="Platos enviados por cocina, con su ficha técnica y su auditoría de Food Cost"
    >
      <div className="mb-4">
        <Link
          to="/admin"
          className="inline-flex min-h-[44px] items-center gap-1.5 text-sm font-medium text-ee-primary hover:text-ee-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring rounded-ee-sm transition-colors duration-150 motion-reduce:transition-none"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          <span>Volver al Panel de Administración</span>
        </Link>
      </div>

      <div
        data-testid="dish-review-split-pane"
        className="grid grid-cols-1 lg:grid-cols-[380px_minmax(0,1fr)] gap-6 items-start"
      >
        <GlassCard
          as="section"
          variant="elevated"
          padding="none"
          radius="lg"
          aria-label="Bandeja de platos"
          className={`overflow-hidden ${isDetailActive ? 'hidden lg:block' : 'block'}`}
        >
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
            selectedDishId={selectedDishId}
            onSelect={openDish}
            onRetry={inbox.refresh}
          />
          <InboxPagination
            page={inbox.page}
            totalPages={inbox.totalPages}
            hasNext={inbox.hasNext}
            onPageChange={inbox.setPage}
          />
        </GlassCard>

        <GlassCard
          as="section"
          variant="elevated"
          padding="lg"
          radius="lg"
          aria-label="Detalle del plato seleccionado"
          className={isDetailActive ? 'block' : 'hidden lg:block'}
        >
          {isDetailActive && (
            <div className="mb-4 lg:hidden">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={closeDetailOnMobile}
                className="min-h-[44px] min-w-[44px] gap-1.5 border border-ee-border-subtle"
              >
                <ArrowLeft className="h-4 w-4" aria-hidden="true" />
                <span>Volver a la bandeja</span>
              </Button>
            </div>
          )}

          {isDetailLoading && (
            <div data-testid="detail-loading" aria-busy="true" className="space-y-3">
              <span className="sr-only">Cargando la ficha del plato…</span>
              <LoadingSkeleton variant="block" announce={false} />
              <LoadingSkeleton variant="text" lines={3} announce={false} />
            </div>
          )}

          {!isDetailLoading && detailError && (
            <div data-testid="detail-error">
              <FeedbackState
                variant="danger"
                title="No se pudo cargar la ficha"
                message={detailError}
              />
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
            <div data-testid="detail-placeholder" className="py-10">
              <EmptyState
                title="Elegí un plato de la bandeja"
                description="Acá vas a ver su ficha técnica, el desglose del Food Cost y las condiciones que le faltan para publicarse."
              />
            </div>
          )}
        </GlassCard>
      </div>
    </WorkspaceLayout>
  );
};

export default AdminDishReviewPage;
