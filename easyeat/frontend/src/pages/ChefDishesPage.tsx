import React, { useState, useEffect, useCallback } from 'react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { Link } from 'react-router-dom';
import { dishApi } from '@/api/dishApi';
import { DishStatusBadge } from '@/components/dish/DishStatusBadge';
import { ChefDishPauseToggle } from '@/components/chef/ChefDishPauseToggle';
import { useChefDishesSync } from '@/hooks/useChefDishesSync';
import type { DishSummaryResponse } from '@/types/dish';

export const ChefDishesPage: React.FC = () => {
  const [dishes, setDishes] = useState<DishSummaryResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

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
    } catch (err: unknown) {
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

  const handleStatusChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newStatus = e.target.value;
    setStatusFilter(newStatus);
    setPage(0);
  };

  const handleDeleteDish = async (dishId: string, dishName: string) => {
    const confirmed = window.confirm(`¿Estás seguro de que deseas eliminar permanentemente el borrador "${dishName}"?`);
    if (!confirmed) return;

    try {
      await dishApi.deleteDish(dishId);
      await fetchDishes(statusFilter, page);
    } catch (err: unknown) {
      alert('Error al eliminar el borrador.');
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
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-100">
              Gestión de Platos
            </h1>
            <p className="text-sm text-zinc-500 dark:text-zinc-400">
              Crea borradores, configura recetas y gestiona la disponibilidad en tiempo real.
            </p>
          </div>
          <Link
            to="/cocina/platos/nuevo"
            className="inline-flex items-center justify-center px-4 py-2 text-sm font-medium text-white bg-amber-600 hover:bg-amber-700 dark:bg-amber-500 dark:hover:bg-amber-600 rounded-lg shadow-sm transition"
          >
            Nuevo Plato
          </Link>
        </div>

        {/* Filters */}
        <div className="flex items-center gap-4 bg-white dark:bg-zinc-900 p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-sm">
          <label htmlFor="status-filter" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
            Filtrar por estado:
          </label>
          <select
            id="status-filter"
            value={statusFilter}
            onChange={handleStatusChange}
            className="text-sm bg-zinc-50 dark:bg-zinc-800 border border-zinc-300 dark:border-zinc-700 rounded-md px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-amber-500"
          >
            <option value="ALL">Todos los estados</option>
            <option value="DRAFT">Borrador</option>
            <option value="PENDING_PRICE">Pendiente de Aprobación</option>
            <option value="PUBLISHED">Publicado</option>
            <option value="CANCELLED">Descartado</option>
          </select>
        </div>

        {errorMessage && (
          <div className="p-4 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 text-sm rounded-lg border border-red-200 dark:border-red-800">
            {errorMessage}
          </div>
        )}

        {isLoading ? (
          <div className="flex justify-center items-center py-12 text-zinc-500">
            Cargando platos...
          </div>
        ) : mergedDishes.length === 0 ? (
          <div className="text-center py-12 bg-white dark:bg-zinc-900 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-sm">
            <p className="text-zinc-500 dark:text-zinc-400 text-sm">
              No hay platos registrados en este establecimiento.
            </p>
          </div>
        ) : (
          <div className="bg-white dark:bg-zinc-900 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-800/50 text-xs font-semibold text-zinc-600 dark:text-zinc-400">
                    <th className="py-3 px-4">Plato</th>
                    <th className="py-3 px-4">Categoría</th>
                    <th className="py-3 px-4">Estado</th>
                    <th className="py-3 px-4">Estado Operativo</th>
                    <th className="py-3 px-4 text-right">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800 text-sm">
                  {mergedDishes.map((dish) => (
                    <tr key={dish.id} className="hover:bg-zinc-50 dark:hover:bg-zinc-800/40 transition">
                      <td className="py-3.5 px-4">
                        {dish.status === 'CANCELLED' ? (
                          <span className="font-medium text-zinc-900 dark:text-zinc-100">
                            {dish.name}
                          </span>
                        ) : (
                          <Link
                            to={`/cocina/platos/${dish.id}`}
                            className="font-medium text-zinc-900 dark:text-zinc-100 hover:text-amber-600 dark:hover:text-amber-400 transition"
                          >
                            {dish.name}
                          </Link>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-zinc-600 dark:text-zinc-300">
                        {dish.category}
                      </td>
                      <td className="py-3.5 px-4">
                        <DishStatusBadge status={dish.status} />
                      </td>
                      <td className="py-3.5 px-4">
                        {dish.status === 'PUBLISHED' ? (
                          <ChefDishPauseToggle
                            dishId={dish.id}
                            initialPaused={Boolean(dish.pausedByChef)}
                            onToggle={handleTogglePause}
                          />
                        ) : (
                          <span className="text-xs text-zinc-400 italic">No publicable</span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-right space-x-2">
                        {dish.status !== 'CANCELLED' && (
                          <Link
                            to={`/cocina/platos/${dish.id}`}
                            className="text-xs font-medium text-amber-600 hover:text-amber-700 dark:text-amber-400 dark:hover:text-amber-300"
                          >
                            Editar
                          </Link>
                        )}
                        {dish.status === 'DRAFT' && (
                          <button
                            type="button"
                            onClick={() => handleDeleteDish(dish.id, dish.name)}
                            className="text-xs font-medium text-red-600 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
                          >
                            Eliminar
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 bg-zinc-50 dark:bg-zinc-800/40 border-t border-zinc-200 dark:border-zinc-800">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="px-3 py-1 text-xs font-medium bg-white dark:bg-zinc-800 border border-zinc-300 dark:border-zinc-700 rounded-md disabled:opacity-50"
                >
                  Anterior
                </button>
                <span className="text-xs text-zinc-500 dark:text-zinc-400">
                  Página {page + 1} de {totalPages}
                </span>
                <button
                  type="button"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  className="px-3 py-1 text-xs font-medium bg-white dark:bg-zinc-800 border border-zinc-300 dark:border-zinc-700 rounded-md disabled:opacity-50"
                >
                  Siguiente
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </WorkspaceLayout>
  );
};

export default ChefDishesPage;
