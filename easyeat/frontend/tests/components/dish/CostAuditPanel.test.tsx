import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import CostAuditPanel from '@/components/dish/CostAuditPanel';
import { FoodCostAuditResponse } from '@/types/dish';

describe('CostAuditPanel Component (ADM-5.1.2)', () => {
  const sampleAudit: FoodCostAuditResponse = {
    dishId: 'd1',
    dishName: 'Pizza Napolitana',
    totalFoodCost: 2050.0,
    hasUnpricedIngredients: false,
    warningNotes: [],
    requiredDietaryRestrictions: ['CELIAC', 'DAIRY'],
    items: [
      {
        ingredientId: 'i1',
        ingredientName: 'Harina Sin Gluten',
        recipeQuantity: 0.3,
        unitOfMeasure: 'KG',
        unitPurchaseCost: 1500.0,
        lineSubtotal: 450.0,
        unpricedWarning: false,
      },
      {
        ingredientId: 'i2',
        ingredientName: 'Mozzarella',
        recipeQuantity: 0.2,
        unitOfMeasure: 'KG',
        unitPurchaseCost: 8000.0,
        lineSubtotal: 1600.0,
        unpricedWarning: false,
      },
    ],
  };

  it('renders dish name, itemized ingredient lines, dietary labels and total food cost', () => {
    render(<CostAuditPanel auditData={sampleAudit} />);

    expect(screen.getByText('Pizza Napolitana')).toBeInTheDocument();
    expect(screen.getByText('Harina Sin Gluten')).toBeInTheDocument();
    expect(screen.getByText('Mozzarella')).toBeInTheDocument();
    expect(screen.getByText('$2050.00')).toBeInTheDocument();
    expect(screen.getByText('CELIAC')).toBeInTheDocument();
    expect(screen.getByText('DAIRY')).toBeInTheDocument();
    // Quantity column reads `recipeQuantity` and keeps the decimal with es-AR formatting.
    expect(screen.getByText('0,3')).toBeInTheDocument();
    expect(screen.getByText('0,2')).toBeInTheDocument();
  });

  it('displays warning alert when hasUnpricedIngredients is true', () => {
    const unpricedAudit: FoodCostAuditResponse = {
      ...sampleAudit,
      hasUnpricedIngredients: true,
      warningNotes: ['Insumo sin precio de adquisición: Agua Filtrada'],
      items: [
        {
          ingredientId: 'i3',
          ingredientName: 'Agua Filtrada',
          recipeQuantity: 1,
          unitOfMeasure: 'L',
          unitPurchaseCost: 0,
          lineSubtotal: 0,
          unpricedWarning: true,
        },
      ],
      totalFoodCost: 0,
    };

    render(<CostAuditPanel auditData={unpricedAudit} />);

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/Advertencia de Insumos sin Precio/i)).toBeInTheDocument();
    expect(screen.getByText(/Insumo sin precio de adquisición: Agua Filtrada/i)).toBeInTheDocument();
    expect(screen.getByText('Sin costo')).toBeInTheDocument();
  });
});

