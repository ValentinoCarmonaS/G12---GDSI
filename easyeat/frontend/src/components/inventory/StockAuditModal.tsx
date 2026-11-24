import React, { useState, useEffect } from 'react';
import {
  AdminIngredientDetailResponse,
  StockAuditEntryResponse,
} from '../../types/inventory';
import { inventoryApi } from '../../api/inventoryApi';
import { AccessibleModal } from '../diner/AccessibleModal';
import { GlassCard } from '../admin/GlassCard';

export interface StockAuditModalProps {
  isOpen: boolean;
  onClose: () => void;
  ingredient: AdminIngredientDetailResponse | null;
}

export const StockAuditModal: React.FC<StockAuditModalProps> = ({
  isOpen,
  onClose,
  ingredient,
}) => {
  const [auditEntries, setAuditEntries] = useState<StockAuditEntryResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen && ingredient) {
      setIsLoading(true);
      setError(null);
      inventoryApi
        .getAuditHistory(ingredient.id)
        .then((data) => {
          setAuditEntries(data);
        })
        .catch(() => {
          setError('Error al cargar la pista de auditoría.');
        })
        .finally(() => {
          setIsLoading(false);
        });
    } else {
      setAuditEntries([]);
      setError(null);
    }
  }, [isOpen, ingredient]);

  if (!ingredient) return null;

  const unit = ingredient.unitOfMeasure;

  return (
    <AccessibleModal
      isOpen={isOpen}
      onClose={onClose}
      title="Pista de Auditoría de Stock"
      subtitle={`${ingredient.name} — Categoría: ${ingredient.category} — Stock Actual: ${ingredient.currentStock} ${unit}`}
      size="lg"
    >
      <div className="space-y-4">
        {error && (
          <div
            role="alert"
            className="p-3 text-sm rounded-ee-sm bg-rose-50 border border-rose-200 text-rose-800"
          >
            {error}
          </div>
        )}

        <div className="max-h-[55vh] overflow-y-auto space-y-2.5 pr-1">
          {isLoading ? (
            <GlassCard variant="subtle" padding="md" className="text-center text-sm text-ee-text-muted">
              Cargando pista de auditoría...
            </GlassCard>
          ) : auditEntries.length === 0 ? (
            <GlassCard variant="subtle" padding="md" className="text-center text-sm text-ee-text-muted">
              No se registran movimientos de stock para este ingrediente.
            </GlassCard>
          ) : (
            auditEntries.map((entry) => {
              const isPositive = entry.netDelta > 0;
              return (
                <GlassCard key={entry.id} variant="subtle" padding="sm" as="article" className="space-y-2 text-xs">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-semibold text-ee-text">
                      {entry.adminName || 'Sistema'}
                    </span>
                    <span className="font-ee-mono tabular-nums text-ee-text-muted">
                      {new Date(entry.createdAt).toLocaleString()}
                    </span>
                  </div>
                  <div className="flex flex-wrap items-center gap-3">
                    <span
                      className={`px-2 py-0.5 rounded-full font-semibold border ${
                        isPositive
                          ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                          : 'bg-rose-50 text-rose-800 border-rose-200'
                      }`}
                    >
                      {isPositive ? `+${entry.netDelta}` : entry.netDelta} {unit}
                    </span>
                    <span className="font-ee-mono tabular-nums text-ee-text-muted">
                      {entry.previousStock} → <strong className="text-ee-text">{entry.newStock} {unit}</strong>
                    </span>
                    {entry.financialLoss !== undefined && entry.financialLoss !== null && entry.financialLoss > 0 && (
                      <span className="font-ee-mono tabular-nums text-rose-700 font-semibold">
                        Pérdida: ${entry.financialLoss.toFixed(2)}
                      </span>
                    )}
                  </div>
                  {entry.justification && (
                    <p className="text-ee-text-muted">{entry.justification}</p>
                  )}
                </GlassCard>
              );
            })
          )}
        </div>

        <div className="flex justify-end pt-2 border-t border-ee-border-subtle">
          <button
            type="button"
            onClick={onClose}
            className="min-h-[44px] px-4 py-2 text-sm font-medium text-ee-text bg-white border border-ee-border-subtle rounded-ee-sm hover:bg-slate-50"
          >
            Cerrar
          </button>
        </div>
      </div>
    </AccessibleModal>
  );
};

export default StockAuditModal;
