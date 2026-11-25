import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { ChefIngredientTable } from '../components/chef/ChefIngredientTable';
import { FeedbackState } from '../components/primitives/FeedbackState';
import { inventoryApi } from '../api/inventoryApi';
import {
  ChefIngredientItemResponse,
  ChefIngredientPageResponse,
  IngredientCategory,
} from '../types/inventory';

export const ChefIngredientsPage: React.FC = () => {
  const [items, setItems] = useState<ChefIngredientItemResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchCatalog = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response: ChefIngredientPageResponse = await inventoryApi.getChefCatalog({
        page,
        size: 20,
        search: searchTerm.trim() || undefined,
        category: selectedCategory === 'ALL' ? undefined : (selectedCategory as IngredientCategory),
      });

      setItems(response.items || []);
      setTotalPages(response.totalPages || 1);
      setTotalElements(response.totalElements || 0);
      setHasNext(response.hasNext || false);
    } catch {
      setError('Error al consultar el catálogo de materias primas de cocina.');
    } finally {
      setIsLoading(false);
    }
  }, [page, searchTerm, selectedCategory]);

  useEffect(() => {
    fetchCatalog();
  }, [fetchCatalog]);

  return (
    <WorkspaceLayout
      title="Catálogo de Ingredientes de Cocina"
      subtitle="Exploración y disponibilidad de materias primas para despacho y elaboración"
    >
      <div className="space-y-6">
        {/* Navigation & Filters Bar */}
        <div className="flex flex-col items-start justify-between gap-4 border-b-2 border-ee-border-strong pb-5 md:flex-row md:items-end">
          <div className="space-y-3">
            <Link
              to="/cocina"
              className="inline-flex min-h-[48px] items-center rounded-ee-sm border-2 border-ee-border-strong bg-ee-surface px-4 py-2 text-sm font-semibold text-ee-text shadow-ee-elevated transition-colors hover:bg-ee-surface-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
            >
              ← Volver al Panel KDS de Cocina
            </Link>
            <h2 className="font-ee-display text-2xl font-bold tracking-tight text-ee-text">
              Materias Primas Disponibles
            </h2>
          </div>

          <div className="flex w-full flex-col items-stretch gap-3 sm:flex-row sm:items-center md:w-auto">
            <div className="w-full sm:w-64">
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value);
                  setPage(0);
                }}
                placeholder="Buscar materia prima..."
                className="min-h-[48px] w-full rounded-ee-sm border-2 border-ee-border-strong bg-ee-surface px-3.5 py-2 text-sm text-ee-text placeholder-ee-text-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
              />
            </div>

            <div className="flex w-full items-center gap-2 sm:w-auto">
              <label
                htmlFor="chefCategoryFilter"
                className="whitespace-nowrap text-xs font-semibold uppercase tracking-wider text-ee-text-secondary"
              >
                Categoría:
              </label>
              <select
                id="chefCategoryFilter"
                value={selectedCategory}
                onChange={(e) => {
                  setSelectedCategory(e.target.value);
                  setPage(0);
                }}
                className="min-h-[48px] flex-1 rounded-ee-sm border-2 border-ee-border-strong bg-ee-surface px-3 py-2 text-sm font-medium text-ee-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring sm:flex-none"
              >
                <option value="ALL">Todas</option>
                <option value="Produce">Produce</option>
                <option value="Meat">Meat</option>
                <option value="Dairy">Dairy</option>
                <option value="Dry Goods">Dry Goods</option>
                <option value="Spices">Spices</option>
              </select>
            </div>
          </div>
        </div>

        {error && (
          <FeedbackState
            variant="danger"
            message={error}
            actionLabel="Reintentar"
            onAction={fetchCatalog}
          />
        )}

        {/* Brutalist Card Grid */}
        <ChefIngredientTable items={items} isLoading={isLoading} />

        {/* Brutalist Pagination Controls */}
        <div className="flex flex-col items-center justify-between gap-3 rounded-ee-sm border-2 border-ee-border-strong bg-ee-surface px-4 py-3 shadow-ee-elevated sm:flex-row sm:px-6">
          <div className="font-ee-mono text-xs font-semibold text-ee-text-secondary">
            {`Página ${page + 1} de ${totalPages} (${totalElements} materias primas)`}
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPage((prev) => Math.max(0, prev - 1))}
              disabled={page === 0 || isLoading}
              className="min-h-[48px] rounded-ee-sm border-2 border-ee-border-strong bg-ee-surface px-4 py-2 text-xs font-bold uppercase tracking-wider text-ee-text transition-colors hover:bg-ee-surface-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring disabled:cursor-not-allowed disabled:opacity-40"
            >
              Anterior
            </button>
            <button
              type="button"
              onClick={() => setPage((prev) => prev + 1)}
              disabled={!hasNext || isLoading}
              className="min-h-[48px] rounded-ee-sm border-2 border-ee-border-strong bg-ee-surface px-4 py-2 text-xs font-bold uppercase tracking-wider text-ee-text transition-colors hover:bg-ee-surface-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring disabled:cursor-not-allowed disabled:opacity-40"
            >
              Siguiente
            </button>
          </div>
        </div>
      </div>
    </WorkspaceLayout>
  );
};

export default ChefIngredientsPage;
