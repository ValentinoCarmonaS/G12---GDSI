import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ChefDishEditorPage } from '@/pages/ChefDishEditorPage';
import { dishApi } from '@/api/dishApi';
import { inventoryApi } from '@/api/inventoryApi';
import type { DishDetailResponse } from '@/types/dish';
import type { ChefIngredientPageResponse } from '@/types/inventory';

vi.mock('@/api/dishApi', () => ({
  dishApi: {
    getDishDetail: vi.fn(),
    createDishDraft: vi.fn(),
    updateDish: vi.fn(),
    updateRecipe: vi.fn(),
    removeRecipeIngredient: vi.fn(),
    uploadPhoto: vi.fn(),
    deletePhoto: vi.fn(),
  },
}));

vi.mock('@/api/inventoryApi', () => ({
  inventoryApi: {
    getChefCatalog: vi.fn(),
  },
}));

describe('User Story 2 User Journey Integration Test (T040, CHF-2.2.2)', () => {
  const mockDishDetail: DishDetailResponse = {
    id: 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
    tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    name: 'Risotto de Hongos',
    category: 'Platos principales',
    description: 'Arroz cremoso con hongos',
    photoUrl: null,
    status: 'DRAFT',
    allergensConfirmed: false,
    restrictionCodes: [],
    recipe: [],
    isCompleteForReview: false,
    missingConditions: ['La receta debe contener al menos un ingrediente'],
    createdById: 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
    createdAt: '2026-10-27T10:00:00Z',
    updatedAt: '2026-10-27T10:00:00Z',
  };

  const mockCatalogResponse: ChefIngredientPageResponse = {
    items: [
      {
        id: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
        name: 'Arroz Carnaroli',
        category: 'Dry Goods',
        unitOfMeasure: 'g',
        stockStatus: 'In Stock',
        currentStock: 5000,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(dishApi.getDishDetail).mockResolvedValue(mockDishDetail);
    vi.mocked(inventoryApi.getChefCatalog).mockResolvedValue(mockCatalogResponse);
    vi.mocked(dishApi.updateRecipe).mockResolvedValue({
      dishId: mockDishDetail.id,
      ingredients: [
        {
          ingredientId: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
          ingredientName: 'Arroz Carnaroli',
          quantity: 200.0,
          unitOfMeasure: 'g',
          isIngredientActive: true,
        },
      ],
      recipe: [
        {
          ingredientId: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
          ingredientName: 'Arroz Carnaroli',
          quantity: 200.0,
          unitOfMeasure: 'g',
          isIngredientActive: true,
        },
      ],
    });
  });

  const renderEditor = (dishId = 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01') =>
    render(
      <MemoryRouter initialEntries={[`/cocina/platos/${dishId}`]}>
        <Routes>
          <Route path="/cocina/platos/:id" element={<ChefDishEditorPage />} />
        </Routes>
      </MemoryRouter>
    );

  it('completes the full recipe composition user journey: load dish -> switch to recipe tab -> select ingredient -> set quantity -> save recipe', async () => {
    const user = userEvent.setup();
    renderEditor();

    // 1. Chef loads dish editor
    await waitFor(() => {
      expect(screen.getByText(/Risotto de Hongos/i)).toBeInTheDocument();
    });

    // 2. Chef switches to Recipe BOM step tab
    const recipeTab = await screen.findByRole('button', { name: /receta.*escandallo|paso 2/i });
    await user.click(recipeTab);

    // 3. Chef opens ingredient modal and selects Arroz Carnaroli
    const addIngredientBtn = await screen.findByRole('button', { name: /agregar ingrediente/i });
    await user.click(addIngredientBtn);

    const selectBtn = await screen.findByRole('button', { name: /seleccionar/i });
    await user.click(selectBtn);

    // 4. Ingredient appears in table; Chef specifies quantity
    const qtyInput = await screen.findByDisplayValue('1');
    await user.clear(qtyInput);
    await user.type(qtyInput, '200');

    // 5. Chef clicks "Guardar Receta"
    const saveRecipeBtn = screen.getByRole('button', { name: /guardar receta/i });
    await user.click(saveRecipeBtn);

    // 6. Verify API was called with correct payload
    await waitFor(() => {
      expect(dishApi.updateRecipe).toHaveBeenCalledWith(
        mockDishDetail.id,
        expect.objectContaining({
          ingredients: expect.arrayContaining([
            expect.objectContaining({
              ingredientId: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
              quantity: 200,
            }),
          ]),
        })
      );
    });
  });
});
