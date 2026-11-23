import React from 'react';
import { AlertTriangle, CheckCircle2, XCircle } from 'lucide-react';
import { DishStatusBadge } from '@/components/dish/DishStatusBadge';
import { StatusBadge } from '@/components/primitives';
import { FoodCostBreakdownTable } from './FoodCostBreakdownTable';
import type {
  AcknowledgementKind,
  DishReviewDetail,
  PublicationGateCondition,
} from '@/types/dishReview';

export interface DishAuditPanelProps {
  detail: DishReviewDetail;
}

const GATE_CONDITION_COPY: Record<PublicationGateCondition, string> = {
  RETAIL_PRICE_MISSING: 'Falta fijar el precio de venta: tiene que ser mayor que cero.',
  EMPTY_RECIPE:
    'La receta no tiene ningún insumo activo. Pedile al Chef que la complete o reemplace los insumos discontinuados.',
  ALLERGENS_UNCONFIRMED:
    'El Chef todavía no confirmó la declaración de alérgenos de la ficha técnica.',
};

const ACKNOWLEDGEMENT_COPY: Record<AcknowledgementKind, string> = {
  UNPRICED_INGREDIENTS:
    'Hay insumos sin costo cargado: el Food Cost mostrado es incompleto. Vas a tener que confirmarlo al publicar.',
  NEGATIVE_MARGIN:
    'El precio queda por debajo del Food Cost, así que el margen es negativo. Vas a tener que confirmarlo al publicar.',
};

export const DishAuditPanel: React.FC<DishAuditPanelProps> = ({ detail }) => {
  const restrictions = detail.foodCostAudit?.requiredDietaryRestrictions ?? [];
  const gate = detail.publicationGate;

  return (
    <section aria-label="Ficha técnica y auditoría de costos" className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4 border-b border-ee-border-subtle pb-4">
        <div className="min-w-0 flex-1">
          <h2 className="font-ee-display text-xl font-bold text-ee-text">{detail.name}</h2>
          <p className="mt-0.5 text-sm text-ee-text-secondary">
            {detail.category ?? 'Sin categoría'}
          </p>
          {detail.description && (
            <p className="mt-2 text-sm text-ee-text leading-relaxed">{detail.description}</p>
          )}
          <p data-testid="audit-author" className="mt-2 text-xs text-ee-text-secondary">
            Chef: <span className="font-medium text-ee-text">{detail.authorName ?? 'Autor no registrado'}</span>
            {detail.authorDeactivated && (
              <span className="ml-2 text-xs text-ee-text-secondary">(cuenta suspendida)</span>
            )}
          </p>
        </div>
        <DishStatusBadge status={detail.status} />
      </header>

      <div data-testid="dietary-labels" className="flex flex-wrap items-center gap-2">
        <span className="font-ee-mono text-xs uppercase tracking-wider text-ee-text-secondary">
          Restricciones
        </span>
        {restrictions.length === 0 ? (
          <span className="text-sm text-ee-text-secondary">Sin restricciones declaradas</span>
        ) : (
          restrictions.map((code) => (
            <StatusBadge key={code} variant="info">
              {code}
            </StatusBadge>
          ))
        )}
      </div>

      {detail.recipeAbsent || !detail.foodCostAudit ? (
        <div
          data-testid="recipe-absent-state"
          className="rounded-ee-md border border-ee-warning-border bg-ee-warning-bg px-4 py-5 text-ee-warning-fg"
        >
          <div className="flex items-start gap-2.5">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <div>
              <p className="font-semibold text-sm">Este plato todavía no tiene receta</p>
              <p className="mt-1 text-sm">
                Sin insumos cargados no se puede calcular el Food Cost ni el margen, y el plato{' '}
                <strong>no se puede publicar</strong> hasta que la receta exista.
              </p>
            </div>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          {detail.foodCostAudit.hasUnpricedIngredients && (
            <div
              data-testid="unpriced-warning-summary"
              role="status"
              className="rounded-ee-md border border-ee-warning-border bg-ee-warning-bg px-4 py-3 text-sm text-ee-warning-fg"
            >
              <div className="flex items-start gap-2.5">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                <div>
                  <p className="font-semibold">El Food Cost mostrado es incompleto</p>
                  {detail.foodCostAudit.warningNotes.length > 0 && (
                    <ul className="mt-1 list-disc list-inside space-y-0.5">
                      {detail.foodCostAudit.warningNotes.map((note) => (
                        <li key={note}>{note}</li>
                      ))}
                    </ul>
                  )}
                  <p className="mt-1">
                    No incluye los insumos sin costo cargado, así que el margen real es menor que el
                    mostrado.
                  </p>
                </div>
              </div>
            </div>
          )}
          <FoodCostBreakdownTable audit={detail.foodCostAudit} />
        </div>
      )}

      <div className="space-y-2.5 rounded-ee-md border border-ee-border-subtle bg-ee-surface-subtle/50 p-4">
        <p
          data-testid="gate-status"
          className={`flex items-center gap-2 text-sm font-semibold ${
            gate.satisfied ? 'text-ee-success-fg' : 'text-ee-text'
          }`}
        >
          {gate.satisfied ? (
            <CheckCircle2 className="h-4 w-4 shrink-0" aria-hidden="true" />
          ) : (
            <XCircle className="h-4 w-4 shrink-0 text-ee-danger-fg" aria-hidden="true" />
          )}
          <span>
            {gate.satisfied
              ? 'Cumple las condiciones de publicación: está lista para publicar.'
              : 'No cumple las condiciones de publicación todavía.'}
          </span>
        </p>

        {gate.unmetConditions.length > 0 && (
          <ul className="space-y-1.5 pl-1">
            {gate.unmetConditions.map((condition) => (
              <li
                key={condition}
                data-testid="gate-condition"
                className="flex items-start gap-2 rounded-ee-sm border border-ee-danger-border bg-ee-danger-bg px-3 py-2 text-sm text-ee-danger-fg"
              >
                <XCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                <span>{GATE_CONDITION_COPY[condition]}</span>
              </li>
            ))}
          </ul>
        )}

        {gate.requiredAcknowledgements.length > 0 && (
          <ul className="space-y-1.5 pl-1">
            {gate.requiredAcknowledgements.map((kind) => (
              <li
                key={kind}
                data-testid="gate-acknowledgement"
                className="flex items-start gap-2 rounded-ee-sm border border-ee-warning-border bg-ee-warning-bg px-3 py-2 text-sm text-ee-warning-fg"
              >
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                <span>{ACKNOWLEDGEMENT_COPY[kind]}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
};

export default DishAuditPanel;
