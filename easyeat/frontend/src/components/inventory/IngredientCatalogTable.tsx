import React, { useState, useMemo } from 'react';
import { AlertTriangle } from 'lucide-react';
import {
  AdminIngredientDetailResponse,
  IngredientStatus,
} from '../../types/inventory';
import { GlassCard } from '../admin/GlassCard';

export interface IngredientCatalogTableProps {
  ingredients: AdminIngredientDetailResponse[];
  onEdit: (ingredient: AdminIngredientDetailResponse) => void;
  onAdjustStock?: (ingredient: AdminIngredientDetailResponse) => void;
  onViewAudit?: (ingredient: AdminIngredientDetailResponse) => void;
  onStatusTransition?: (id: string, newStatus: IngredientStatus) => Promise<void>;
  onStatusChange?: (id: string, newStatus: IngredientStatus) => Promise<void>;
  onToggleCriticalOnly?: (criticalOnly: boolean) => void;
  criticalOnly?: boolean;
  isLoading?: boolean;
}

const STATUS_LABELS: Record<IngredientStatus, string> = {
  ACTIVE: 'Activo',
  DISCONTINUED: 'Desactivado',
};

const STATUS_BADGE_CLASSES: Record<IngredientStatus, string> = {
  ACTIVE: 'bg-emerald-50 text-emerald-800 border-emerald-200',
  DISCONTINUED: 'bg-amber-50 text-amber-800 border-amber-200',
};

export const IngredientCatalogTable: React.FC<IngredientCatalogTableProps> = ({
  ingredients,
  onEdit,
  onAdjustStock,
  onViewAudit,
  onStatusTransition,
  onStatusChange,
  onToggleCriticalOnly,
  criticalOnly = false,
  isLoading = false,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [transitioningId, setTransitioningId] = useState<string | null>(null);

  const [localCriticalOnly, setLocalCriticalOnly] = useState<boolean>(criticalOnly);

  const isCriticalFilterActive = onToggleCriticalOnly !== undefined ? criticalOnly : localCriticalOnly;

  const handleCriticalFilterChange = (checked: boolean) => {
    setLocalCriticalOnly(checked);
    if (onToggleCriticalOnly) {
      onToggleCriticalOnly(checked);
    }
  };

  const filteredIngredients = useMemo(() => {
    return ingredients.filter((item) => {
      const matchesSearch = item.name.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesCategory = categoryFilter === 'ALL' || item.category === categoryFilter;
      const matchesStatus = statusFilter === 'ALL' || item.status === statusFilter;
      const matchesCritical = !isCriticalFilterActive || item.isCritical === true;
      return matchesSearch && matchesCategory && matchesStatus && matchesCritical;
    });
  }, [ingredients, searchTerm, categoryFilter, statusFilter, isCriticalFilterActive]);

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
      <GlassCard
        variant="subtle"
        padding="md"
        className="flex flex-col lg:flex-row gap-3 items-stretch lg:items-center justify-between"
      >
        <div className="w-full lg:w-72">
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Buscar por nombre..."
            aria-label="Buscar por nombre"
            className="w-full min-h-[44px] px-3 py-2 border border-ee-border-subtle rounded-ee-sm text-sm text-ee-text placeholder-ee-text-muted focus:outline-none focus:ring-2 focus:ring-ee-focus-ring bg-white/90"
          />
        </div>

        <div className="flex flex-wrap items-center gap-3 w-full lg:w-auto">
          <label className="flex items-center gap-2 min-h-[44px] cursor-pointer bg-amber-50 hover:bg-amber-100 border border-amber-300 px-3 py-2 rounded-ee-sm text-xs font-semibold text-amber-900 transition-colors">
            <input
              type="checkbox"
              checked={isCriticalFilterActive}
              onChange={(e) => handleCriticalFilterChange(e.target.checked)}
              aria-label="Solo insumos en stock crítico"
              className="rounded border-amber-400 text-amber-700 focus:ring-ee-focus-ring h-4 w-4"
            />
            <AlertTriangle className="w-4 h-4 text-amber-700 shrink-0" aria-hidden="true" />
            <span>Solo insumos en stock crítico</span>
          </label>

          <div className="flex items-center gap-1.5">
            <label htmlFor="filterCategorySelect" className="text-xs text-ee-text-muted font-medium whitespace-nowrap">
              Categoría:
            </label>
            <select
              id="filterCategorySelect"
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="min-h-[44px] px-2.5 py-2 border border-ee-border-subtle rounded-ee-sm text-xs text-ee-text bg-white/90 focus:outline-none focus:ring-2 focus:ring-ee-focus-ring"
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
            <label htmlFor="filterStatusSelect" className="text-xs text-ee-text-muted font-medium whitespace-nowrap">
              Estado:
            </label>
            <select
              id="filterStatusSelect"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="min-h-[44px] px-2.5 py-2 border border-ee-border-subtle rounded-ee-sm text-xs text-ee-text bg-white/90 focus:outline-none focus:ring-2 focus:ring-ee-focus-ring"
            >
              <option value="ALL">Todos</option>
              <option value="ACTIVE">Activo</option>
              <option value="DISCONTINUED">Desactivado</option>
            </select>
          </div>
        </div>
      </GlassCard>

      {isLoading ? (
        <GlassCard variant="elevated" padding="lg" className="text-center text-sm text-ee-text-muted">
          Cargando catálogo de insumos...
        </GlassCard>
      ) : filteredIngredients.length === 0 ? (
        <GlassCard variant="elevated" padding="lg" className="text-center text-sm text-ee-text-muted">
          No se encontraron insumos registrados.
        </GlassCard>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {filteredIngredients.map((item) => {
            const isDiscontinued = item.status === 'DISCONTINUED';
            const isTransitioning = transitioningId === item.id;

            return (
              <GlassCard
                key={item.id}
                variant="interactive"
                padding="md"
                as="article"
                className="flex flex-col justify-between gap-4"
              >
                <div className="space-y-3">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <h3 className="text-base font-semibold text-ee-text truncate">
                        {item.name}
                      </h3>
                      <p className="text-xs text-ee-text-muted">{item.category}</p>
                    </div>
                    <span
                      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border shrink-0 ${
                        STATUS_BADGE_CLASSES[item.status] || 'bg-slate-100 text-slate-800 border-slate-200'
                      }`}
                    >
                      {STATUS_LABELS[item.status] || item.status}
                    </span>
                  </div>

                  {item.isCritical && (
                    <div
                      role="status"
                      className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-ee-sm bg-amber-50 border border-amber-300 text-amber-900 text-xs font-semibold"
                    >
                      <AlertTriangle className="w-3.5 h-3.5 text-amber-700 shrink-0" aria-hidden="true" />
                      <span>⚠️ Stock Crítico</span>
                    </div>
                  )}

                  <div className="grid grid-cols-2 gap-2 pt-1 text-xs">
                    <div className="p-2 rounded-ee-sm bg-white/60 border border-ee-border-subtle">
                      <span className="block text-ee-text-muted">Stock actual</span>
                      <span className="font-ee-mono tabular-nums font-bold text-sm text-ee-text">
                        {item.currentStock} {item.unitOfMeasure}
                      </span>
                    </div>
                    <div className="p-2 rounded-ee-sm bg-white/60 border border-ee-border-subtle">
                      <span className="block text-ee-text-muted">Costo unitario</span>
                      <span className="font-ee-mono tabular-nums font-bold text-sm text-ee-text">
                        {item.unitPurchaseCost !== null && item.unitPurchaseCost !== undefined
                          ? `$${item.unitPurchaseCost.toFixed(2)}`
                          : '—'}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex flex-wrap items-center justify-end gap-2 pt-3 border-t border-ee-border-subtle">
                  {onAdjustStock && (
                    <button
                      type="button"
                      onClick={() => onAdjustStock(item)}
                      className="min-h-[44px] px-3 py-2 text-xs font-semibold text-ee-primary bg-ee-primary/5 hover:bg-ee-primary/10 rounded-ee-sm border border-ee-primary/25 transition-colors focus:outline-none focus:ring-2 focus:ring-ee-focus-ring"
                    >
                      Ajustar Stock
                    </button>
                  )}
                  {onViewAudit && (
                    <button
                      type="button"
                      onClick={() => onViewAudit(item)}
                      className="min-h-[44px] px-3 py-2 text-xs font-semibold text-ee-text bg-white/80 hover:bg-white rounded-ee-sm border border-ee-border-subtle transition-colors focus:outline-none focus:ring-2 focus:ring-ee-focus-ring"
                    >
                      Auditoría
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => onEdit(item)}
                    className="min-h-[44px] px-3 py-2 text-xs font-semibold text-ee-text bg-white/80 hover:bg-white rounded-ee-sm border border-ee-border-subtle transition-colors focus:outline-none focus:ring-2 focus:ring-ee-focus-ring"
                  >
                    Editar
                  </button>
                  {(onStatusTransition || onStatusChange) && (
                    <button
                      type="button"
                      disabled={isTransitioning}
                      onClick={() =>
                        handleStatusTransition(item.id, isDiscontinued ? 'ACTIVE' : 'DISCONTINUED')
                      }
                      className={`min-h-[44px] px-3 py-2 text-xs font-semibold rounded-ee-sm border transition-colors focus:outline-none focus:ring-2 focus:ring-ee-focus-ring disabled:opacity-50 ${
                        isDiscontinued
                          ? 'text-emerald-800 bg-emerald-50 hover:bg-emerald-100 border-emerald-200'
                          : 'text-amber-900 bg-amber-50 hover:bg-amber-100 border-amber-200'
                      }`}
                    >
                      {isDiscontinued ? 'Reactivar' : 'Desactivar'}
                    </button>
                  )}
                </div>
              </GlassCard>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default IngredientCatalogTable;
