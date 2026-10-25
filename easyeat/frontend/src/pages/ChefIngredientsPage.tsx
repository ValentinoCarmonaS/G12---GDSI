import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { ChefIngredientTable } from '../components/chef/ChefIngredientTable';
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
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
          <div>
            <Link
              to="/cocina"
              className="inline-flex items-center text-xs font-semibold text-orange-600 hover:text-orange-800 mb-2"
            >
              ← Volver al Panel KDS de Cocina
            </Link>
            <h2 className="text-2xl font-bold text-gray-900 tracking-tight">
              Materias Primas Disponibles
            </h2>
          </div>

          <div className="flex flex-col sm:flex-row items-center gap-3 w-full md:w-auto">
            <div className="w-full sm:w-64">
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value);
                  setPage(0);
                }}
                placeholder="Buscar materia prima..."
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-orange-500 bg-white"
              />
            </div>

            <div className="flex items-center gap-1.5 w-full sm:w-auto">
              <label htmlFor="chefCategoryFilter" className="text-xs text-gray-500 font-medium whitespace-nowrap">
                Categoría:
              </label>
              <select
                id="chefCategoryFilter"
                value={selectedCategory}
                onChange={(e) => {
                  setSelectedCategory(e.target.value);
                  setPage(0);
                }}
                className="px-2.5 py-2 border border-gray-300 rounded-md text-xs bg-white focus:outline-none focus:ring-2 focus:ring-orange-500"
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
          <div
            role="alert"
            className="p-3 text-sm rounded-md bg-red-50 border border-red-200 text-red-700 animate-fade-in"
          >
            {error}
          </div>
        )}

        {/* Table */}
        <ChefIngredientTable items={items} isLoading={isLoading} />

        {/* Pagination Controls */}
        <div className="flex items-center justify-between border-t border-gray-200 bg-white px-4 py-3 rounded-lg shadow-xs sm:px-6">
          <div className="text-xs text-gray-600">
            {`Página ${page + 1} de ${totalPages} (${totalElements} materias primas)`}
          </div>

          <div className="flex items-center space-x-2">
            <button
              type="button"
              onClick={() => setPage((prev) => Math.max(0, prev - 1))}
              disabled={page === 0 || isLoading}
              className="px-3 py-1.5 border border-gray-300 rounded-md text-xs font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Anterior
            </button>
            <button
              type="button"
              onClick={() => setPage((prev) => prev + 1)}
              disabled={!hasNext || isLoading}
              className="px-3 py-1.5 border border-gray-300 rounded-md text-xs font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
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
