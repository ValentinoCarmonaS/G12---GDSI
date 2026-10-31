import React from 'react';
import { DishStatusBadge } from '@/components/dish/DishStatusBadge';
import { FoodCostBreakdownTable } from './FoodCostBreakdownTable';
import type {
  AcknowledgementKind,
  DishReviewDetail,
  PublicationGateCondition,
} from '@/types/dishReview';

export interface DishAuditPanelProps {
  detail: DishReviewDetail;
}

/**
 * One actionable line per unmet condition (FR-032). The condition names are short; what the
 * Administrator needs is the next step, so each maps to a sentence rather than a label.
 */
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

/**
 * The culinary file as the Administrator reads it before deciding (FR-006).
 *
 * Composes identity, declared labels and the itemized cost. The gate is shown as **status**, never
 * as enforcement: FR-015 re-evaluates all three conditions when the approval action arrives, so
 * what is drawn here is what the Administrator was told and nothing the transition relies on.
 */
export const DishAuditPanel: React.FC<DishAuditPanelProps> = ({ detail }) => {
  const restrictions = detail.foodCostAudit?.requiredDietaryRestrictions ?? [];
  const gate = detail.publicationGate;

  return (
    <section aria-label="Ficha técnica y auditoría de costos" className="space-y-6">
      <header className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <h2 className="text-xl font-semibold text-gray-900">{detail.name}</h2>
          <p className="text-sm text-gray-500">{detail.category ?? 'Sin categoría'}</p>
          {detail.description && (
            <p className="mt-2 text-sm text-gray-700">{detail.description}</p>
          )}
          <p data-testid="audit-author" className="mt-2 text-sm text-gray-600">
            {detail.authorName ?? 'Autor no registrado'}
            {detail.authorDeactivated && (
              <span className="ml-2 text-xs text-zinc-600">(cuenta suspendida)</span>
            )}
          </p>
        </div>
        <DishStatusBadge status={detail.status} />
      </header>

      <div data-testid="dietary-labels" className="flex flex-wrap items-center gap-2">
        <span className="text-xs uppercase tracking-wide text-gray-500">Restricciones</span>
        {restrictions.length === 0 ? (
          <span className="text-sm text-gray-500">Sin restricciones declaradas</span>
        ) : (
          restrictions.map((code) => (
            <span
              key={code}
              className="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-sky-100 text-sky-800 border border-sky-300"
            >
              {code}
            </span>
          ))
        )}
      </div>

      {/* FR-037: the absence of a recipe is reported as a state of the dish, not as a failure. */}
      {detail.recipeAbsent || !detail.foodCostAudit ? (
        <div
          data-testid="recipe-absent-state"
          className="px-4 py-6 border border-amber-200 bg-amber-50 rounded"
        >
          <p className="font-medium text-amber-900">Este plato todavía no tiene receta</p>
          <p className="mt-1 text-sm text-amber-800">
            Sin insumos cargados no se puede calcular el Food Cost ni el margen, y el plato{' '}
            <strong>no se puede publicar</strong> hasta que la receta exista.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {detail.foodCostAudit.hasUnpricedIngredients && (
            <div
              data-testid="unpriced-warning-summary"
              role="status"
              className="px-3 py-2 border border-amber-200 bg-amber-50 rounded text-sm text-amber-900"
            >
              {/*
                "Incompleto", not "incorrecto". The figure is not wrong - every priced line is
                counted correctly - it is missing the lines that have no cost. Telling an
                Administrator their cost is wrong when it is understated would send them to
                check the wrong thing.
              */}
              <p className="font-medium">El Food Cost mostrado es incompleto</p>
              <ul className="mt-1 list-disc list-inside">
                {detail.foodCostAudit.warningNotes.map((note) => (
                  <li key={note}>{note}</li>
                ))}
              </ul>
              <p className="mt-1">
                No incluye los insumos sin costo cargado, así que el margen real es menor que el
                mostrado.
              </p>
            </div>
          )}
          <FoodCostBreakdownTable audit={detail.foodCostAudit} />
        </div>
      )}

      <div className="space-y-2">
        <p
          data-testid="gate-status"
          className={`text-sm font-medium ${
            gate.satisfied ? 'text-emerald-800' : 'text-gray-900'
          }`}
        >
          {gate.satisfied
            ? 'Cumple las condiciones de publicación: está lista para publicar.'
            : 'No cumple las condiciones de publicación todavía.'}
        </p>

        {gate.unmetConditions.length > 0 && (
          <ul className="space-y-1">
            {gate.unmetConditions.map((condition) => (
              <li
                key={condition}
                data-testid="gate-condition"
                className="text-sm text-red-800 flex gap-2"
              >
                <span aria-hidden="true">•</span>
                <span>{GATE_CONDITION_COPY[condition]}</span>
              </li>
            ))}
          </ul>
        )}

        {/*
          Acknowledgements are rendered apart from the conditions and never among them. FR-017
          makes them an additional precondition and never a gate failure, so listing one as unmet
          would tell the Administrator a publishable dish is blocked.
        */}
        {gate.requiredAcknowledgements.length > 0 && (
          <ul className="space-y-1">
            {gate.requiredAcknowledgements.map((kind) => (
              <li
                key={kind}
                data-testid="gate-acknowledgement"
                className="text-sm text-amber-800 flex gap-2"
              >
                <span aria-hidden="true">⚠</span>
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
