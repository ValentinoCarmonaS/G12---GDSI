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
  },
}));

describe('User Story 3 User Journey Integration Test (T051, CHF-2.2.3)', () => {
  const dishId = 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01';

  const mockDraftDish: DishDetailResponse = {
    id: dishId,
    tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    name: 'Risotto de Hongos',
    category: 'Platos principales',
    description: 'Arroz cremoso con hongos',
    photoUrl: null,
    status: 'DRAFT',
    allergensConfirmed: false,
    restrictionCodes: [],
    recipe: [
      {
        ingredientId: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
        ingredientName: 'Arroz Carnaroli',
        quantity: 150,
        unitOfMeasure: 'g',
        isIngredientActive: true,
      },
    ],
    isCompleteForReview: false,
    missingConditions: ['Se debe confirmar la sección de alérgenos'],
    createdById: 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
    createdAt: '2026-10-27T10:00:00Z',
    updatedAt: '2026-10-27T10:00:00Z',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('completes the allergen labeling journey: navigates to Step 3, selects tags, confirms section and persists state', async () => {
    const user = userEvent.setup();

    vi.mocked(dishApi.getDishDetail)
      .mockResolvedValueOnce(mockDraftDish)
      .mockResolvedValueOnce({
        ...mockDraftDish,
        allergensConfirmed: true,
        restrictionCodes: ['VEGAN', 'DAIRY'],
        isCompleteForReview: true,
        missingConditions: [],
      });

    vi.mocked(dishApi.updateDishAllergens).mockResolvedValueOnce({
      dishId,
      restrictionCodes: ['VEGAN', 'DAIRY'],
      allergensConfirmed: true,
      updatedAt: '2026-10-27T10:05:00Z',
    });

    render(
      <MemoryRouter initialEntries={[`/cocina/platos/${dishId}`]}>
        <Routes>
          <Route path="/cocina/platos/:id" element={<ChefDishEditorPage />} />
        </Routes>
      </MemoryRouter>
    );

    // Initial load
    expect(await screen.findByText('Ficha Técnica: Risotto de Hongos')).toBeInTheDocument();

    // Click Step 3 tab
    const step3Tab = screen.getByRole('button', { name: /Paso 3: Alérgenos/i });
    await user.click(step3Tab);

    // Verify AllergenStep renders
    expect(await screen.findByText(/Estilos de Vida Dietarios/i)).toBeInTheDocument();
    expect(screen.getByText(/Alérgenos Comunes/i)).toBeInTheDocument();

    // Select VEGAN and DAIRY
    const veganCheckbox = screen.getByRole('checkbox', { name: /Vegano/i });
    const dairyCheckbox = screen.getByRole('checkbox', { name: /Lácteos/i });
    await user.click(veganCheckbox);
    await user.click(dairyCheckbox);

    // Check confirm toggle
    const confirmToggle = screen.getByRole('checkbox', { name: /Confirmar sección de alérgenos/i });
    await user.click(confirmToggle);

    // Click save
    const saveButton = screen.getByRole('button', { name: /Guardar y Confirmar Alérgenos/i });
    await user.click(saveButton);

    await waitFor(() => {
      expect(dishApi.updateDishAllergens).toHaveBeenCalledWith(dishId, {
        restrictionCodes: expect.arrayContaining(['VEGAN', 'DAIRY']),
        confirmed: true,
      });
    });

    expect(await screen.findByText(/Alérgenos actualizados correctamente/i)).toBeInTheDocument();
  });

  it('renders read-only AllergenStep when dish status is PENDING_PRICE', async () => {
    const user = userEvent.setup();

    vi.mocked(dishApi.getDishDetail).mockResolvedValueOnce({
      ...mockDraftDish,
      status: 'PENDING_PRICE',
      allergensConfirmed: true,
      restrictionCodes: ['CELIAC'],
    });

    render(
      <MemoryRouter initialEntries={[`/cocina/platos/${dishId}`]}>
        <Routes>
          <Route path="/cocina/platos/:id" element={<ChefDishEditorPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('Ficha Técnica: Risotto de Hongos')).toBeInTheDocument();

    const step3Tab = screen.getByRole('button', { name: /Paso 3: Alérgenos/i });
    await user.click(step3Tab);

    const celiacCheckbox = await screen.findByRole('checkbox', { name: /Celíaco/i });
    expect(celiacCheckbox).toBeDisabled();

    const saveButton = screen.getByRole('button', { name: /Guardar y Confirmar Alérgenos/i });
    expect(saveButton).toBeDisabled();
  });
});
