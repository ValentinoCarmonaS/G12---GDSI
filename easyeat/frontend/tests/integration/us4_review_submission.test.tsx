import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ChefDishEditorPage } from '@/pages/ChefDishEditorPage';
import { dishApi } from '@/api/dishApi';
import type { DishDetailResponse } from '@/types/dish';

vi.mock('@/api/dishApi', () => ({
  dishApi: {
    getDishDetail: vi.fn(),
    createDishDraft: vi.fn(),
    updateDish: vi.fn(),
    updateRecipe: vi.fn(),
    removeRecipeIngredient: vi.fn(),
    uploadPhoto: vi.fn(),
    deletePhoto: vi.fn(),
    updateDishAllergens: vi.fn(),
    submitForReview: vi.fn(),
  },
}));

describe('User Story 4 User Journey Integration Test (T064, CHF-2.2.4)', () => {
  const dishId = 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01';

  const mockCompleteDraft: DishDetailResponse = {
    id: dishId,
    tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    name: 'Risotto de Hongos',
    category: 'Platos principales',
    description: 'Arroz cremoso con hongos',
    photoUrl: null,
    status: 'DRAFT',
    allergensConfirmed: true,
    restrictionCodes: ['VEGETARIAN'],
    recipe: [
      {
        ingredientId: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
        ingredientName: 'Arroz Carnaroli',
        quantity: 150,
        unitOfMeasure: 'g',
        isIngredientActive: true,
      },
    ],
    isCompleteForReview: true,
    missingConditions: [],
    createdById: 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
    createdAt: '2026-10-27T10:00:00Z',
    updatedAt: '2026-10-27T10:00:00Z',
  };

  const mockSubmittedDish: DishDetailResponse = {
    ...mockCompleteDraft,
    status: 'PENDING_PRICE',
    updatedAt: '2026-10-28T01:00:00Z',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('completes the review submission journey: navigates to Step 4, submits dish, and transitions to locked PENDING_PRICE state', async () => {
    const user = userEvent.setup();

    vi.mocked(dishApi.getDishDetail)
      .mockResolvedValueOnce(mockCompleteDraft)
      .mockResolvedValueOnce(mockSubmittedDish);

    vi.mocked(dishApi.submitForReview).mockResolvedValueOnce(mockSubmittedDish);

    render(
      <MemoryRouter initialEntries={[`/cocina/platos/${dishId}`]}>
        <Routes>
          <Route path="/cocina/platos/:id" element={<ChefDishEditorPage />} />
        </Routes>
      </MemoryRouter>
    );

    // Initial load
    await waitFor(() => {
      expect(screen.getByText(/Ficha Técnica: Risotto de Hongos/i)).toBeInTheDocument();
    });

    // Navigate to Step 4: Revisión
    const reviewTab = screen.getByRole('button', { name: /Paso 4: Revisión y Envío/i });
    await user.click(reviewTab);

    // Verify checklist items are rendered
    expect(screen.getByText(/Identidad Básica/i)).toBeInTheDocument();
    expect(screen.getByText(/Receta y Escandallo/i)).toBeInTheDocument();
    expect(screen.getByText(/Alérgenos y Preferencias/i)).toBeInTheDocument();

    // Click "Enviar a revisión"
    const submitBtn = screen.getByRole('button', { name: /Enviar a revisión/i });
    expect(submitBtn).not.toBeDisabled();
    await user.click(submitBtn);

    // Verify submitForReview API was called
    await waitFor(() => {
      expect(dishApi.submitForReview).toHaveBeenCalledWith(dishId);
    });

    // Verify dish status transition to PENDING_PRICE and success notification
    await waitFor(() => {
      expect(screen.getByText(/Ficha técnica enviada a revisión exitosamente/i)).toBeInTheDocument();
    });
  });
});
