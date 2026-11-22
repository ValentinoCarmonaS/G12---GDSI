import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { formatAuditQuantity } from '@/utils/foodCostAudit';
import { GlassCard } from '@/components/admin/GlassCard';
import { Button, StatusBadge } from '@/components/primitives';
import { FoodCostAuditResponse } from '@/types/dish';

interface CostAuditPanelProps {
  auditData: FoodCostAuditResponse;
  onRefresh?: () => void;
}

export const CostAuditPanel: React.FC<CostAuditPanelProps> = ({ auditData, onRefresh }) => {
  return (
    <GlassCard as="section" variant="elevated" padding="lg" radius="lg" className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-ee-border-subtle pb-4">
        <div className="min-w-0">
          <h2 className="font-ee-display text-xl font-bold text-ee-text">
            Auditoría Financiera de Costos (Food Cost)
          </h2>
          <p className="mt-1 text-sm text-ee-text-secondary">
            Plato: <span className="font-semibold text-ee-text">{auditData.dishName}</span>
          </p>
        </div>
        {onRefresh && (
          <Button
            type="button"
            variant="ghost"
            size="md"
            onClick={onRefresh}
            className="gap-2 border border-ee-border-subtle"
          >
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
            <span>Actualizar Costos</span>
          </Button>
        )}
      </div>

      {auditData.hasUnpricedIngredients && (
        <div
          className="rounded-ee-md border border-ee-warning-border bg-ee-warning-bg p-4 text-ee-warning-fg space-y-1.5"
          role="alert"
        >
          <div className="flex items-start gap-2.5">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <div className="space-y-1">
              <p className="font-semibold text-sm">Advertencia de Insumos sin Precio</p>
              <p className="text-sm">
                Uno o más insumos no tienen costo de adquisición cargado en inventario. El costo
                calculado es parcial.
              </p>
              {auditData.warningNotes.length > 0 && (
                <ul className="list-disc pl-5 text-sm mt-1 space-y-0.5">
                  {auditData.warningNotes.map((note, idx) => (
                    <li key={idx}>{note}</li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      )}

      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-ee-text">
          Desglose de Receta Técnica e Insumos
        </h3>
        <ul className="space-y-2.5">
          {auditData.items.map((item) => (
            <li key={item.ingredientId}>
              <GlassCard
                variant="subtle"
                padding="sm"
                radius="md"
                className={`px-4 py-3 ${
                  item.unpricedWarning ? 'border-ee-warning-border bg-ee-warning-bg/40' : ''
                }`}
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold text-sm text-ee-text">
                        {item.ingredientName}
                      </span>
                      {item.unpricedWarning && (
                        <StatusBadge variant="warning">Sin costo</StatusBadge>
                      )}
                    </div>
                    <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 font-ee-mono tabular-nums text-xs text-ee-text-secondary">
                      <span>{formatAuditQuantity(item)}</span>
                      <span>{item.unitOfMeasure}</span>
                      <span>Costo unit.: ${item.unitPurchaseCost.toFixed(2)}</span>
                    </div>
                  </div>
                  <div className="shrink-0 text-right">
                    <span className="block font-ee-mono text-[11px] uppercase tracking-wider text-ee-text-secondary">
                      Subtotal
                    </span>
                    <span className="font-ee-mono tabular-nums text-sm font-bold text-ee-text">
                      ${item.lineSubtotal.toFixed(2)}
                    </span>
                  </div>
                </div>
              </GlassCard>
            </li>
          ))}
        </ul>

        <GlassCard
          variant="accent"
          padding="md"
          radius="md"
          className="flex flex-wrap items-center justify-between gap-4"
        >
          <span className="text-sm font-semibold text-ee-text">
            Costo Total de Materia Prima (Food Cost):
          </span>
          <span className="font-ee-mono tabular-nums text-xl font-bold text-ee-primary">
            ${auditData.totalFoodCost.toFixed(2)}
          </span>
        </GlassCard>
      </div>

      {auditData.requiredDietaryRestrictions &&
        auditData.requiredDietaryRestrictions.length > 0 && (
          <div className="border-t border-ee-border-subtle pt-4">
            <h3 className="text-sm font-semibold text-ee-text mb-2.5">
              Etiquetas y Restricciones Dietarias Declaradas
            </h3>
            <div className="flex flex-wrap gap-2">
              {auditData.requiredDietaryRestrictions.map((code) => (
                <StatusBadge key={code} variant="info">
                  {code}
                </StatusBadge>
              ))}
            </div>
          </div>
        )}
    </GlassCard>
  );
};

export default CostAuditPanel;
