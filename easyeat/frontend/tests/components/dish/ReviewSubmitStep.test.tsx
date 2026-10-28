import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReviewSubmitStep } from '@/components/dish/ReviewSubmitStep';
import type { DishDetailResponse } from '@/types/dish';

describe('ReviewSubmitStep Component (T062, CHF-2.2.4, FR-016, FR-017, FR-022)', () => {
  const mockOnSubmit = vi.fn();

  const mockCompleteDish: DishDetailResponse = {
    id: 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
    name: 'Risotto de Hongos',
    category: 'Platos principales',
    description: 'Arroz con hongos portobello',
    photoUrl: '/api/v1/dishes/d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01/photo',
    status: 'DRAFT',
    allergensConfirmed: true,
    restrictionCodes: ['VEGETARIAN', 'DAIRY'],
    recipe: [
      {
        ingredientId: 'i1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
        ingredientName: 'Arroz Carnaroli',
        quantity: 200,
        unitOfMeasure: 'g',
        isIngredientActive: true,
      },
    ],
  };

  const mockIncompleteDish: DishDetailResponse = {
    id: 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
    name: 'Risotto de Hongos',
    category: 'Platos principales',
    status: 'DRAFT',
    allergensConfirmed: false,
    restrictionCodes: [],
    recipe: [],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders completeness checklist items for Identity, Recipe BOM, and Allergens', () => {
    render(
      <ReviewSubmitStep
        dish={mockIncompleteDish}
        onSubmitForReview={mockOnSubmit}
      />
    );

    expect(screen.getByText(/Identidad Básica/i)).toBeInTheDocument();
    expect(screen.getByText(/Receta y Escandallo/i)).toBeInTheDocument();
    expect(screen.getByText(/Alérgenos y Preferencias/i)).toBeInTheDocument();
  });

  it('disables "Enviar a revisión" button and shows warning cues when criteria are unmet', () => {
    render(
      <ReviewSubmitStep
        dish={mockIncompleteDish}
        onSubmitForReview={mockOnSubmit}
      />
    );

    const submitBtn = screen.getByRole('button', { name: /Enviar a revisión/i });
    expect(submitBtn).toBeDisabled();
    expect(screen.getByText(/Faltan requisitos para enviar a revisión/i)).toBeInTheDocument();
  });

  it('enables "Enviar a revisión" button when all completeness conditions are satisfied', async () => {
    const user = userEvent.setup();
    render(
      <ReviewSubmitStep
        dish={mockCompleteDish}
        onSubmitForReview={mockOnSubmit}
      />
    );

    const submitBtn = screen.getByRole('button', { name: /Enviar a revisión/i });
    expect(submitBtn).not.toBeDisabled();

    await user.click(submitBtn);
    expect(mockOnSubmit).toHaveBeenCalledTimes(1);
  });

  it('displays locked read-only banner when dish is in PENDING_PRICE status', () => {
    const pendingPriceDish: DishDetailResponse = {
      ...mockCompleteDish,
      status: 'PENDING_PRICE',
    };

    render(
      <ReviewSubmitStep
        dish={pendingPriceDish}
        onSubmitForReview={mockOnSubmit}
        isReadOnly={true}
      />
    );

    expect(screen.getByText(/Ficha técnica en revisión/i)).toBeInTheDocument();
    const submitBtn = screen.getByRole('button', { name: /Enviado a revisión/i });
    expect(submitBtn).toBeDisabled();
  });
});
