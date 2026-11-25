import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Plus, Trash2, Edit3, ChevronLeft, ChevronRight } from 'lucide-react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { BrutalistCard } from '../components/chef/BrutalistCard';
import { BrutalistModal } from '../components/chef/BrutalistModal';
import { LoadingSkeleton } from '../components/primitives/LoadingSkeleton';
import { EmptyState } from '../components/primitives/EmptyState';
import { FeedbackState } from '../components/primitives/FeedbackState';
import { dishApi } from '@/api/dishApi';
import { DishStatusBadge } from '@/components/dish/DishStatusBadge';
import { ChefDishPauseToggle } from '@/components/chef/ChefDishPauseToggle';
import { useChefDishesSync } from '@/hooks/useChefDishesSync';
import type { DishSummaryResponse } from '@/types/dish';

const STATUS_CHIPS = [
  { value: 'ALL', label: 'Todos los estados' },
  { value: 'DRAFT', label: 'Borrador' },
  { value: 'PENDING_PRICE', label: 'Pendiente de Aprobación' },
  { value: 'PUBLISHED', label: 'Publicado' },
  { value: 'CANCELLED', label: 'Descartado' },
] as const;

export const ChefDishesPage: React.FC = () => {
  const navigate = useNavigate();
  const [dishes, setDishes] = useState<DishSummaryResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; name: string } | null>(null);
  const [isDeleting, setIsDeleting] = useState<boolean>(false);

  const { items: operationalItems } = useChefDishesSync(true);

  const fetchDishes = useCallback(async (status: string, pageNum: number) => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const response = await dishApi.listDishes({
        status,
        page: pageNum,
        size: 20,
      });
      setDishes(response.content || []);
      setTotalPages(response.totalPages || 0);
    } catch {
      setErrorMessage('Error al cargar la lista de platos.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDishes(statusFilter, page);
  }, [fetchDishes, statusFilter, page]);

  // Merge live operational status from polling into current dish list
  const mergedDishes = React.useMemo(() => {
    return dishes.map((dish) => {
      const op = operationalItems.find((o) => o.dishId === dish.id);
      if (op) {
        return {
          ...dish,
          pausedByChef: op.pausedByChef,
        };
      }
      return dish;
    });
  }, [dishes, operationalItems]);

  const handleStatusSelect = (newStatus: string) => {
    setStatusFilter(newStatus);
    setPage(0);
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget || isDeleting) return;
    setIsDeleting(true);
    setErrorMessage(null);

    try {
      await dishApi.deleteDish(deleteTarget.id);
      setDeleteTarget(null);
      await fetchDishes(statusFilter, page);
    } catch {
      setDeleteTarget(null);
      setErrorMessage('Error al eliminar el borrador.');
    } finally {
      setIsDeleting(false);
    }
  };

  const handleTogglePause = async (dishId: string, nextPaused: boolean) => {
    await dishApi.toggleChefPause(dishId, nextPaused);
    setDishes((prev) =>
      prev.map((d) => (d.id === dishId ? { ...d, pausedByChef: nextPaused } : d))
    );
  };

  return (
    <WorkspaceLayout title="Catálogo de Platos">
      <div className="space-y-6 max-w-7xl mx-auto">
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b-2 border-ee-border-strong pb-4">
          <div>
            <h1 className="font-ee-display text-3xl font-extrabold tracking-tight text-ee-text">
              Gestión de Platos
            </h1>
            <p className="text-sm font-medium text-ee-text-secondary">
              Crea borradores, configura recetas y gestiona la disponibilidad en tiempo real.
            </p>
          </div>
          <Link
            to="/cocina/platos/nuevo"
            className="inline-flex min-h-[48px] items-center justify-center gap-2 px-5 py-2.5 text-sm font-bold text-ee-accent-fg bg-ee-accent hover:bg-ee-accent-hover border-2 border-ee-border-strong rounded-ee-sm shadow-ee-elevated transition-transform duration-150 motion-reduce:transition-none active:translate-x-0.5 active:translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
          >
            <Plus className="w-4 h-4 shrink-0" aria-hidden="true" />
            <span>Nuevo Plato</span>
          </Link>
        </div>

        {/* Filter Chips + Single View-Level Operational Status Label */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div
            role="group"
            aria-label="Filtrar por estado"
            className="flex flex-wrap items-center gap-2"
          >
            {STATUS_CHIPS.map((chip) => {
              const isSelected = statusFilter === chip.value;
              return (
                <button
                  key={chip.value}
                  type="button"
                  aria-pressed={isSelected}
                  onClick={() => handleStatusSelect(chip.value)}
                  className={`inline-flex min-h-[48px] items-center justify-center px-4 py-2 text-sm font-bold rounded-ee-sm border-2 border-ee-border-strong transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring ${isSelected
                    ? 'bg-ee-text text-ee-surface shadow-ee-elevated'
                    : 'bg-ee-surface text-ee-text hover:bg-ee-surface-subtle'
                    }`}
                >
                  {chip.label}
                </button>
              );
            })}
          </div>

          <div className="text-xs font-bold uppercase tracking-wider text-ee-text-secondary">
            Estado Operativo
          </div>
        </div>

        {errorMessage && (
          <FeedbackState
            variant="danger"
            title="Ocurrió un problema"
            message={errorMessage}
            actionLabel="Reintentar"
            onAction={() => fetchDishes(statusFilter, page)}
          />
        )}

        {isLoading ? (
          <div
            data-testid="chef-dishes-loading"
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
          >
            {Array.from({ length: 6 }, (_, index) => (
              <BrutalistCard key={index} variant="default" padding="md">
                <LoadingSkeleton variant="text" lines={3} label="Cargando catálogo de platos..." />
              </BrutalistCard>
            ))}
          </div>
        ) : mergedDishes.length === 0 ? (
          <EmptyState
            title="No hay platos registrados en este establecimiento."
            description="Creá un nuevo plato para comenzar a armar la carta de cocina."
            actionLabel="Nuevo Plato"
            onAction={() => navigate('/cocina/platos/nuevo')}
          />
        ) : (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {mergedDishes.map((dish) => (
                <BrutalistCard
                  key={dish.id}
                  variant="elevated"
                  padding="md"
                  as="article"
                  className="flex flex-col justify-between gap-4"
                >
                  <div className="space-y-3">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        {dish.status === 'CANCELLED' ? (
                          <h2 className="font-ee-display text-xl font-bold text-ee-text truncate">
                            {dish.name}
                          </h2>
                        ) : (
                          <Link
                            to={`/cocina/platos/${dish.id}`}
                            className="font-ee-display text-xl font-bold text-ee-text hover:underline underline-offset-4 block truncate focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
                          >
                            {dish.name}
                          </Link>
                        )}
                        <p className="text-xs font-semibold uppercase tracking-wide text-ee-text-secondary mt-0.5">
                          {dish.category}
                        </p>
                      </div>
                      <DishStatusBadge status={dish.status} />
                    </div>

                    <div className="pt-2 border-t-2 border-ee-border-subtle">
                      {dish.status === 'PUBLISHED' ? (
                        <ChefDishPauseToggle
                          dishId={dish.id}
                          initialPaused={Boolean(dish.pausedByChef)}
                          onToggle={handleTogglePause}
                        />
                      ) : (
                        <span className="inline-flex min-h-[56px] items-center text-xs font-semibold uppercase tracking-wide text-ee-text-muted">
                          No publicable
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center justify-end gap-2 pt-2 border-t-2 border-ee-border-subtle">
                    {dish.status !== 'CANCELLED' && (
                      <Link
                        to={`/cocina/platos/${dish.id}`}
                        className="inline-flex min-h-[48px] items-center justify-center gap-1.5 px-4 py-2 text-sm font-bold text-ee-text bg-ee-surface hover:bg-ee-surface-subtle border-2 border-ee-border-strong rounded-ee-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
                      >
                        <Edit3 className="w-4 h-4 shrink-0" aria-hidden="true" />
                        <span>Editar</span>
                      </Link>
                    )}
                    {dish.status === 'DRAFT' && (
                      <button
                        type="button"
                        onClick={() => setDeleteTarget({ id: dish.id, name: dish.name })}
                        className="inline-flex min-h-[48px] items-center justify-center gap-1.5 px-4 py-2 text-sm font-bold text-ee-danger-fg bg-ee-danger-bg border-2 border-ee-danger-border rounded-ee-sm cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
                      >
                        <Trash2 className="w-4 h-4 shrink-0" aria-hidden="true" />
                        <span>Eliminar</span>
                      </button>
                    )}
                  </div>
                </BrutalistCard>
              ))}
            </div>

            {totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 bg-ee-surface border-2 border-ee-border-strong rounded-ee-sm shadow-ee-elevated">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="inline-flex min-h-[48px] items-center gap-1.5 px-4 py-2 text-sm font-bold bg-ee-surface text-ee-text border-2 border-ee-border-strong rounded-ee-sm disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed"
                >
                  <ChevronLeft className="w-4 h-4" aria-hidden="true" />
                  <span>Anterior</span>
                </button>
                <span className="font-ee-mono text-sm font-bold text-ee-text">
                  Página {page + 1} de {totalPages}
                </span>
                <button
                  type="button"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  className="inline-flex min-h-[48px] items-center gap-1.5 px-4 py-2 text-sm font-bold bg-ee-surface text-ee-text border-2 border-ee-border-strong rounded-ee-sm disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed"
                >
                  <span>Siguiente</span>
                  <ChevronRight className="w-4 h-4" aria-hidden="true" />
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      <BrutalistModal
        isOpen={Boolean(deleteTarget)}
        onClose={() => {
          if (!isDeleting) setDeleteTarget(null);
        }}
        title="Eliminar borrador"
        subtitle={deleteTarget ? `Plato: ${deleteTarget.name}` : undefined}
        size="sm"
        dismissible={!isDeleting}
        footer={
          <div className="flex flex-col sm:flex-row justify-end gap-3 w-full">
            <button
              type="button"
              disabled={isDeleting}
              onClick={() => setDeleteTarget(null)}
              className="inline-flex min-h-[56px] items-center justify-center px-5 py-2.5 text-sm font-bold bg-ee-surface text-ee-text border-2 border-ee-border-strong rounded-ee-sm cursor-pointer disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
            >
              Cancelar
            </button>
            <button
              type="button"
              disabled={isDeleting}
              onClick={handleConfirmDelete}
              className="inline-flex min-h-[56px] items-center justify-center px-5 py-2.5 text-sm font-bold bg-ee-danger-bg text-ee-danger-fg border-2 border-ee-danger-border rounded-ee-sm shadow-ee-elevated cursor-pointer disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
            >
              {isDeleting ? 'Eliminando...' : 'Confirmar eliminación'}
            </button>
          </div>
        }
      >
        <p className="text-sm font-medium text-ee-text">
          ¿Estás seguro de que deseas eliminar permanentemente el borrador{' '}
          <strong className="font-bold">{deleteTarget?.name}</strong>? Esta acción no se puede
          deshacer.
        </p>
      </BrutalistModal>
    </WorkspaceLayout>
  );
};

export default ChefDishesPage;
