import React, { useState, useMemo } from 'react';
import {
  AdminIngredientDetailResponse,
  IngredientStatus,
} from '../../types/inventory';

export interface IngredientCatalogTableProps {
  ingredients: AdminIngredientDetailResponse[];
  onEdit: (ingredient: AdminIngredientDetailResponse) => void;
  onAdjustStock?: (ingredient: AdminIngredientDetailResponse) => void;
  onViewAudit?: (ingredient: AdminIngredientDetailResponse) => void;
  onStatusTransition?: (id: string, newStatus: IngredientStatus) => Promise<void>;
  onStatusChange?: (id: string, newStatus: IngredientStatus) => Promise<void>;
  isLoading?: boolean;
}

const STATUS_LABELS: Record<IngredientStatus, string> = {
  ACTIVE: 'Activo',
  DISCONTINUED: 'Desactivado',
};

const STATUS_BADGE_CLASSES: Record<IngredientStatus, string> = {
  ACTIVE: 'bg-green-100 text-green-800 border-green-200',
  DISCONTINUED: 'bg-orange-100 text-orange-800 border-orange-200',
};

export const IngredientCatalogTable: React.FC<IngredientCatalogTableProps> = ({
  ingredients,
  onEdit,
  onAdjustStock,
  onViewAudit,
  onStatusTransition,
  onStatusChange,
  isLoading = false,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [transitioningId, setTransitioningId] = useState<string | null>(null);

  const filteredIngredients = useMemo(() => {
    return ingredients.filter((item) => {
      const matchesSearch = item.name.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesCategory = categoryFilter === 'ALL' || item.category === categoryFilter;
      const matchesStatus = statusFilter === 'ALL' || item.status === statusFilter;
      return matchesSearch && matchesCategory && matchesStatus;
    });
  }, [ingredients, searchTerm, categoryFilter, statusFilter]);

  const handleStatusTransition = async (id: string, nextStatus: IngredientStatus) => {
    setTransitioningId(id);
    try {
      if (onStatusTransition) {
        await onStatusTransition(id, nextStatus);
      } else if (onStatusChange) {
        await onStatusChange(id, nextStatus);
      }
    } finally {
      setTransitioningId(null);
    }
  };

  return (
    <div className="space-y-4">
      {/* Search and Filters Bar */}
      <div className="flex flex-col md:flex-row gap-3 items-center justify-between">
        <div className="w-full md:w-72">
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Buscar por nombre..."
            className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
          />
        </div>

        <div className="flex flex-wrap items-center gap-3 w-full md:w-auto">
          <div className="flex items-center gap-1.5">
            <label htmlFor="filterCategorySelect" className="text-xs text-gray-500 font-medium whitespace-nowrap">
              Categoría:
            </label>
            <select
              id="filterCategorySelect"
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="px-2.5 py-1.5 border border-gray-300 rounded-md text-xs bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">Todas</option>
              <option value="Produce">Produce</option>
              <option value="Meat">Meat</option>
              <option value="Dairy">Dairy</option>
              <option value="Dry Goods">Dry Goods</option>
              <option value="Spices">Spices</option>
            </select>
          </div>

          <div className="flex items-center gap-1.5">
            <label htmlFor="filterStatusSelect" className="text-xs text-gray-500 font-medium whitespace-nowrap">
              Estado:
            </label>
            <select
              id="filterStatusSelect"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-2.5 py-1.5 border border-gray-300 rounded-md text-xs bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">Todos</option>
              <option value="ACTIVE">Activo</option>
              <option value="DISCONTINUED">Desactivado</option>
            </select>
          </div>
        </div>
      </div>

      {/* Catalog Table */}
      <div className="bg-white shadow rounded-lg border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200 text-left text-sm">
            <thead className="bg-gray-50 text-gray-500 font-medium text-xs uppercase tracking-wider">
              <tr>
                <th scope="col" className="px-6 py-3">Ingrediente</th>
                <th scope="col" className="px-6 py-3">Categoría</th>
                <th scope="col" className="px-6 py-3">Unidad</th>
                <th scope="col" className="px-6 py-3">Costo Referencia</th>
                <th scope="col" className="px-6 py-3">Stock Actual</th>
                <th scope="col" className="px-6 py-3">Estado</th>
                <th scope="col" className="px-6 py-3 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {isLoading ? (
                <tr>
                  <td colSpan={7} className="px-6 py-8 text-center text-gray-500">
                    Cargando catálogo maestro...
                  </td>
                </tr>
              ) : filteredIngredients.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-8 text-center text-gray-500">
                    No se encontraron materias primas en el catálogo.
                  </td>
                </tr>
              ) : (
                filteredIngredients.map((item) => {
                  const isTransitioning = transitioningId === item.id;

                  return (
                    <tr key={item.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-6 py-4 font-medium text-gray-900 whitespace-nowrap">
                        {item.name}
                      </td>
                      <td className="px-6 py-4 text-gray-600 whitespace-nowrap text-xs">
                        {item.category}
                      </td>
                      <td className="px-6 py-4 font-mono text-gray-600 whitespace-nowrap text-xs">
                        {item.unitOfMeasure}
                      </td>
                      <td className="px-6 py-4 font-mono font-medium text-gray-900 whitespace-nowrap">
                        ${item.unitPurchaseCost.toFixed(2)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span
                          className={`font-mono text-sm font-semibold ${
                            item.currentStock === 0 ? 'text-red-600' : 'text-gray-900'
                          }`}
                        >
                          {item.currentStock} {item.unitOfMeasure}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span
                          className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                            STATUS_BADGE_CLASSES[item.status] || 'bg-gray-100 text-gray-800'
                          }`}
                        >
                          {STATUS_LABELS[item.status] || item.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-right whitespace-nowrap space-x-1.5">
                        <button
                          type="button"
                          onClick={() => onEdit(item)}
                          disabled={isTransitioning}
                          className="text-blue-600 hover:text-blue-900 font-medium text-xs px-2 py-1 rounded border border-blue-200 hover:bg-blue-50 transition-colors"
                        >
                          Editar
                        </button>

                        {onAdjustStock && (
                          <button
                            type="button"
                            onClick={() => onAdjustStock(item)}
                            disabled={isTransitioning}
                            className="text-amber-600 hover:text-amber-900 font-medium text-xs px-2 py-1 rounded border border-amber-200 hover:bg-amber-50 transition-colors"
                          >
                            Ajustar Stock
                          </button>
                        )}

                        {onViewAudit && (
                          <button
                            type="button"
                            onClick={() => onViewAudit(item)}
                            disabled={isTransitioning}
                            className="text-purple-600 hover:text-purple-900 font-medium text-xs px-2 py-1 rounded border border-purple-200 hover:bg-purple-50 transition-colors"
                          >
                            Auditoría
                          </button>
                        )}

                        {/* Lifecycle Transition Actions (FR-014: Active <-> Discontinued) */}
                        {item.status === 'ACTIVE' ? (
                          <button
                            type="button"
                            onClick={() => handleStatusTransition(item.id, 'DISCONTINUED')}
                            disabled={isTransitioning}
                            className="text-orange-600 hover:text-orange-900 font-medium text-xs px-2 py-1 rounded border border-orange-200 hover:bg-orange-50 transition-colors"
                          >
                            Desactivar
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => handleStatusTransition(item.id, 'ACTIVE')}
                            disabled={isTransitioning}
                            className="text-green-600 hover:text-green-900 font-medium text-xs px-2 py-1 rounded border border-green-200 hover:bg-green-50 transition-colors"
                          >
                            Reactivar
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default IngredientCatalogTable;
