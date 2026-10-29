import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DishAuditPanel } from '../../../src/components/dishreview/DishAuditPanel';
import { FoodCostBreakdownTable } from '../../../src/components/dishreview/FoodCostBreakdownTable';
import type { DishReviewDetail, FoodCostAudit } from '../../../src/types/dishReview';

function audit(overrides: Partial<FoodCostAudit> = {}): FoodCostAudit {
  return {
    dishId: 'dish-1',
    dishName: 'Risotto de Hongos',
    totalFoodCost: 4500,
    hasUnpricedIngredients: false,
    warningNotes: [],
    requiredDietaryRestrictions: ['CELIAC', 'VEGETARIAN'],
    items: [
      {
        ingredientId: 'ing-1',
        ingredientName: 'Arroz Arborio',
        quantity: 0.2,
        unitOfMeasure: 'KG',
        unitPurchaseCost: 7500,
        lineSubtotal: 1500,
        unpricedWarning: false,
      },
      {
        ingredientId: 'ing-2',
        ingredientName: 'Hongos Portobello',
        quantity: 0.15,
        unitOfMeasure: 'KG',
        unitPurchaseCost: 20000,
        lineSubtotal: 3000,
        unpricedWarning: false,
      },
    ],
    ...overrides,
  };
}

function detail(overrides: Partial<DishReviewDetail> = {}): DishReviewDetail {
  return {
    dishId: 'dish-1',
    name: 'Risotto de Hongos',
    description: 'Arborio, portobello y parmesano.',
    category: 'Platos principales',
    status: 'PENDING_PRICE',
    retailPrice: null,
    photoUrl: null,
    authorName: 'Camila Duarte',
    authorDeactivated: false,
    allergensConfirmed: true,
    submittedAt: '2026-10-01T10:00:00Z',
    publishedAt: null,
    reviewCycleCount: 0,
    isResubmission: false,
    margin: {
      foodCost: 4500,
      retailPrice: null,
      marginPct: null,
      marginTolerancePp: null,
      toleranceThresholdPrice: null,
      hasUnpricedIngredients: false,
      marginDerivable: true,
    },
    publicationGate: {
      satisfied: false,
      unmetConditions: ['RETAIL_PRICE_MISSING'],
      requiredAcknowledgements: [],
    },
    recipeAbsent: false,
    foodCostAudit: audit(),
    ...overrides,
  };
}

describe('DishAuditPanel', () => {
  describe('identity and labels (FR-006)', () => {
    it('renders the dish identity the Administrator is deciding about', () => {
      render(<DishAuditPanel detail={detail()} />);

      expect(screen.getByText('Risotto de Hongos')).toBeInTheDocument();
      expect(screen.getByText(/arborio, portobello/i)).toBeInTheDocument();
      expect(screen.getByText('Platos principales')).toBeInTheDocument();
    });

    it('renders every declared dietary and allergen code (FR-006)', () => {
      render(<DishAuditPanel detail={detail()} />);

      expect(screen.getByTestId('dietary-labels')).toHaveTextContent('CELIAC');
      expect(screen.getByTestId('dietary-labels')).toHaveTextContent('VEGETARIAN');
    });

    it('says so when a dish declares no restriction, instead of showing nothing', () => {
      render(
        <DishAuditPanel
          detail={detail({ foodCostAudit: audit({ requiredDietaryRestrictions: [] }) })}
        />
      );

      expect(screen.getByTestId('dietary-labels')).toHaveTextContent(/sin restricciones/i);
    });

    it('renders an unattributed dish as "Autor no registrado" (FR-027)', () => {
      render(<DishAuditPanel detail={detail({ authorName: null })} />);

      expect(screen.getByTestId('audit-author')).toHaveTextContent(/autor no registrado/i);
    });
  });

  describe('the itemized cost breakdown (FR-006)', () => {
    it('shows one row per recipe line with quantity, unit, unit cost and subtotal', () => {
      render(<FoodCostBreakdownTable audit={audit()} />);

      const rows = screen.getAllByTestId('cost-line');
      expect(rows).toHaveLength(2);
      expect(rows[0]).toHaveTextContent('Arroz Arborio');
      expect(rows[0]).toHaveTextContent('KG');
      expect(rows[0]).toHaveTextContent('0,2');
    });

    it('shows the aggregated Food Cost per portion', () => {
      render(<FoodCostBreakdownTable audit={audit()} />);

      expect(screen.getByTestId('food-cost-total')).toHaveTextContent('4.500,00');
    });

    it('renders a line with no declared unit as a dash rather than an empty cell', () => {
      render(
        <FoodCostBreakdownTable
          audit={audit({
            items: [
              {
                ingredientId: 'ing-3',
                ingredientName: 'Caldo de Verduras',
                quantity: 1,
                unitOfMeasure: null,
                unitPurchaseCost: 900,
                lineSubtotal: 900,
                unpricedWarning: false,
              },
            ],
          })}
        />
      );

      const row = screen.getByTestId('cost-line');
      expect(row).toHaveTextContent('Caldo de Verduras');
      expect(row).toHaveTextContent('—');
    });

    it('renders a zero Food Cost as a figure, not as an absence', () => {
      render(
        <FoodCostBreakdownTable audit={audit({ totalFoodCost: 0, items: [] })} />
      );

      // 0.00 is a real cost: a recipe whose every ingredient is unpriced. Showing a dash
      // would make it indistinguishable from "not computed".
      expect(screen.getByTestId('food-cost-total')).toHaveTextContent('0,00');
    });
  });

  describe('the unpriced-ingredient advisory (FR-006)', () => {
    it('marks the specific line whose ingredient has no cost', () => {
      render(
        <FoodCostBreakdownTable
          audit={audit({
            hasUnpricedIngredients: true,
            items: [
              {
                ingredientId: 'ing-3',
                ingredientName: 'Parmesano',
                quantity: 0.05,
                unitOfMeasure: 'KG',
                unitPurchaseCost: 0,
                lineSubtotal: 0,
                unpricedWarning: true,
              },
            ],
          })}
        />
      );

      expect(screen.getByTestId('unpriced-ingredient-badge')).toBeInTheDocument();
      expect(screen.getByTestId('cost-line')).toHaveTextContent('Parmesano');
    });

    it('shows no advisory when every ingredient carries a cost', () => {
      render(<FoodCostBreakdownTable audit={audit()} />);

      expect(screen.queryByTestId('unpriced-ingredient-badge')).not.toBeInTheDocument();
    });

    it('surfaces the panel-level warning explaining the cost is understated', () => {
      render(
        <DishAuditPanel
          detail={detail({
            foodCostAudit: audit({
              hasUnpricedIngredients: true,
              warningNotes: ['Insumo sin precio de adquisición: Parmesano'],
            }),
          })}
        />
      );

      const warning = screen.getByTestId('unpriced-warning-summary');
      expect(warning).toHaveTextContent(/parmesano/i);
      // The number is not wrong, it is incomplete — and saying which it is matters for a
      // decision about money.
      expect(warning).toHaveTextContent(/incompleto|no incluye|subestim/i);
    });
  });

  describe('a dish with no recipe (FR-037)', () => {
    it('explains the absence instead of rendering an empty table', () => {
      render(
        <DishAuditPanel
          detail={detail({
            recipeAbsent: true,
            foodCostAudit: null,
            margin: {
              foodCost: null,
              retailPrice: null,
              marginPct: null,
              marginTolerancePp: null,
              toleranceThresholdPrice: null,
              hasUnpricedIngredients: false,
              marginDerivable: false,
            },
            publicationGate: {
              satisfied: false,
              unmetConditions: ['RETAIL_PRICE_MISSING', 'EMPTY_RECIPE'],
              requiredAcknowledgements: [],
            },
          })}
        />
      );

      expect(screen.getByTestId('recipe-absent-state')).toBeInTheDocument();
      expect(screen.queryByTestId('food-cost-total')).not.toBeInTheDocument();
    });

    it('still renders identity and author, which is the point of FR-037', () => {
      render(
        <DishAuditPanel detail={detail({ recipeAbsent: true, foodCostAudit: null })} />
      );

      expect(screen.getByText('Risotto de Hongos')).toBeInTheDocument();
      expect(screen.getByTestId('audit-author')).toHaveTextContent('Camila Duarte');
    });

    it('states that the dish cannot be published until a recipe exists', () => {
      render(
        <DishAuditPanel detail={detail({ recipeAbsent: true, foodCostAudit: null })} />
      );

      expect(screen.getByTestId('recipe-absent-state')).toHaveTextContent(/no se puede publicar/i);
    });
  });

  describe('the publication gate, shown as status (FR-015)', () => {
    it('gives one actionable line per unmet condition (FR-032)', () => {
      render(
        <DishAuditPanel
          detail={detail({
            publicationGate: {
              satisfied: false,
              unmetConditions: ['RETAIL_PRICE_MISSING', 'ALLERGENS_UNCONFIRMED'],
              requiredAcknowledgements: [],
            },
          })}
        />
      );

      const conditions = screen.getAllByTestId('gate-condition');
      expect(conditions).toHaveLength(2);
      expect(conditions.map((c) => c.textContent).join(' ')).toMatch(/precio/i);
      expect(conditions.map((c) => c.textContent).join(' ')).toMatch(/alérgen|alergen/i);
    });

    it('reports a satisfied gate without listing conditions', () => {
      render(
        <DishAuditPanel
          detail={detail({
            retailPrice: 11250,
            publicationGate: {
              satisfied: true,
              unmetConditions: [],
              requiredAcknowledgements: [],
            },
          })}
        />
      );

      expect(screen.getByTestId('gate-status')).toHaveTextContent(/cumple|lista para publicar/i);
      expect(screen.queryByTestId('gate-condition')).not.toBeInTheDocument();
    });

    it('shows an outstanding acknowledgement without presenting it as a block (FR-017)', () => {
      render(
        <DishAuditPanel
          detail={detail({
            retailPrice: 11250,
            publicationGate: {
              satisfied: true,
              unmetConditions: [],
              requiredAcknowledgements: ['NEGATIVE_MARGIN'],
            },
          })}
        />
      );

      expect(screen.getByTestId('gate-acknowledgement')).toBeInTheDocument();
      expect(screen.queryByTestId('gate-condition')).not.toBeInTheDocument();
      expect(screen.getByTestId('gate-status')).toHaveTextContent(/cumple|lista para publicar/i);
    });
  });
});
