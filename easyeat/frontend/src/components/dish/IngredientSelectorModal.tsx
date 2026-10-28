import React, { useState, useEffect } from 'react';
import { inventoryApi } from '@/api/inventoryApi';
import type { ChefIngredientItemResponse, ChefIngredientPageResponse, IngredientCategory } from '@/types/inventory';

export interface IngredientSelectorModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectIngredient: (ingredient: ChefIngredientItemResponse) => void;
  selectedIngredientIds?: string[];
}

const CATEGORIES: { label: string; value: string }[] = [
  { label: 'Todas las categorías', value: '' },
  { label: 'Produce', value: 'Produce' },
  { label: 'Meat', value: 'Meat' },
  { label: 'Dairy', value: 'Dairy' },
  { label: 'Dry Goods', value: 'Dry Goods' },
  { label: 'Spices', value: 'Spices' },
];

export const IngredientSelectorModal: React.FC<IngredientSelectorModalProps> = ({
  isOpen,
  onClose,
  onSelectIngredient,
  selectedIngredientIds = [],
}) => {
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0);
  const [catalog, setCatalog] = useState<ChefIngredientPageResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;

    let isMounted = true;
    setLoading(true);
    setError(null);

    inventoryApi
      .getChefCatalog({
        page,
        size: 20,
        search: search.trim() || undefined,
        category: (category as IngredientCategory) || undefined,
      })
      .then((data) => {
        if (isMounted) {
          setCatalog(data);
          setLoading(false);
        }
      })
      .catch(() => {
        if (isMounted) {
          setError('Error al cargar el catálogo de ingredientes.');
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [isOpen, search, category, page]);

  if (!isOpen) return null;

  const handleSelect = (ingredient: ChefIngredientItemResponse) => {
    onSelectIngredient(ingredient);
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 overflow-y-auto"
      role="dialog"
      aria-modal="true"
    >
      <div className="bg-gray-900 border border-gray-800 rounded-xl max-w-3xl w-full p-6 shadow-2xl relative text-gray-100 flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-gray-800">
          <div>
            <h2 className="text-xl font-bold text-white tracking-wide">Seleccionar Ingrediente</h2>
            <p className="text-sm text-gray-400 mt-0.5">
              Ingredientes activos disponibles en el catálogo del establecimiento
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-white p-2 rounded-lg hover:bg-gray-800 transition-colors"
            aria-label="Cerrar modal"
          >
            ✕
          </button>
        </div>

        {/* Filters */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 py-4 border-b border-gray-800">
          <div>
            <label htmlFor="ingredient-search" className="block text-xs font-medium text-gray-300 mb-1">
              Buscar por nombre
            </label>
            <input
              id="ingredient-search"
              type="text"
              placeholder="Buscar ingrediente..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              className="w-full bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-amber-500"
            />
          </div>

          <div>
            <label htmlFor="category-filter" className="block text-xs font-medium text-gray-300 mb-1">
              Filtrar por categoría
            </label>
            <select
              id="category-filter"
              value={category}
              onChange={(e) => {
                setCategory(e.target.value);
                setPage(0);
              }}
              className="w-full bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-amber-500"
            >
              {CATEGORIES.map((cat) => (
                <option key={cat.value} value={cat.value}>
                  {cat.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Table Content */}
        <div className="flex-1 overflow-y-auto py-3 min-h-[250px]">
          {loading && (
            <div className="flex justify-center items-center py-12 text-gray-400 text-sm">
              Cargando catálogo de ingredientes...
            </div>
          )}

          {error && (
            <div className="p-3 bg-red-950/60 border border-red-800 text-red-300 text-sm rounded-lg my-4">
              {error}
            </div>
          )}

          {!loading && !error && (!catalog || catalog.items.length === 0) && (
            <div className="text-center py-12 text-gray-500 text-sm">
              No se encontraron ingredientes activos con los filtros indicados.
            </div>
          )}

          {!loading && catalog && catalog.items.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-sm">
                <thead>
                  <tr className="border-b border-gray-800 text-gray-400 text-xs uppercase tracking-wider">
                    <th className="py-2.5 px-3">Ingrediente</th>
                    <th className="py-2.5 px-3">Categoría</th>
                    <th className="py-2.5 px-3 text-center">Unidad</th>
                    <th className="py-2.5 px-3 text-right">Stock</th>
                    <th className="py-2.5 px-3 text-center">Acción</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-800">
                  {catalog.items.map((item) => {
                    const isAlreadyAdded = selectedIngredientIds.includes(item.id);

                    return (
                      <tr key={item.id} className="hover:bg-gray-800/50 transition-colors">
                        <td className="py-2.5 px-3 font-medium text-white">{item.name}</td>
                        <td className="py-2.5 px-3 text-gray-300">{item.category}</td>
                        <td className="py-2.5 px-3 text-center text-gray-400">{item.unitOfMeasure}</td>
                        <td className="py-2.5 px-3 text-right text-gray-300 font-mono text-xs">
                          {item.currentStock} {item.unitOfMeasure}
                        </td>
                        <td className="py-2.5 px-3 text-center">
                          {isAlreadyAdded ? (
                            <span className="inline-block px-2.5 py-1 text-xs font-semibold text-amber-400 bg-amber-950/60 rounded border border-amber-800">
                              Ya en la receta
                            </span>
                          ) : (
                            <button
                              type="button"
                              onClick={() => handleSelect(item)}
                              className="px-3 py-1 bg-amber-500 hover:bg-amber-600 text-black font-semibold text-xs rounded transition-colors"
                            >
                              Seleccionar
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Footer & Pagination */}
        <div className="flex items-center justify-between pt-4 border-t border-gray-800 text-xs text-gray-400">
          <div>
            {catalog && catalog.totalElements > 0 && (
              <span>
                Mostrando {catalog.items.length} de {catalog.totalElements} ingredientes
              </span>
            )}
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="px-3 py-1.5 bg-gray-800 border border-gray-700 rounded hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              Anterior
            </button>
            <button
              type="button"
              disabled={!catalog || catalog.page >= catalog.totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
              className="px-3 py-1.5 bg-gray-800 border border-gray-700 rounded hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              Siguiente
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
