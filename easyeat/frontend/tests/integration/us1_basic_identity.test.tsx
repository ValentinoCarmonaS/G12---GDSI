import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ChefDishesPage } from '@/pages/ChefDishesPage';
import { ChefDishEditorPage } from '@/pages/ChefDishEditorPage';
import { dishApi } from '@/api/dishApi';
import {
  MOCK_DISH_ID_1,
  mockDishDraftDetail,
  createMockDishPage,
  createMockPhotoUploadResponse,
} from '../mocks/dishHandlers';

vi.mock('@/api/dishApi', () => ({
  dishApi: {
    listDishes: vi.fn(),
    createDishDraft: vi.fn(),
    getDishDetail: vi.fn(),
    updateDish: vi.fn(),
    deleteDish: vi.fn(),
    uploadPhoto: vi.fn(),
    deletePhoto: vi.fn(),
  },
}));

describe('User Story 1 User Journey Integration Test (T025, CHF-2.2.1)', () => {
  const mockedDishApi = vi.mocked(dishApi);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderApp = (initialRoute = '/cocina/platos') =>
    render(
      <MemoryRouter initialEntries={[initialRoute]}>
        <Routes>
          <Route path="/cocina/platos" element={<ChefDishesPage />} />
          <Route path="/cocina/platos/nuevo" element={<ChefDishEditorPage />} />
          <Route path="/cocina/platos/:id" element={<ChefDishEditorPage />} />
        </Routes>
      </MemoryRouter>
    );

  it('completes the full User Story 1 journey: list -> new draft -> upload photo -> verify list updated', async () => {
    const user = userEvent.setup();

    // Initial listing has no dishes
    mockedDishApi.listDishes.mockResolvedValueOnce(createMockDishPage([]));

    renderApp('/cocina/platos');

    // 1. Chef arrives at dish list and clicks "Nuevo Plato"
    const newDishButton = await screen.findByRole('link', { name: /nuevo plato/i });
    await user.click(newDishButton);

    // 2. Navigated to /cocina/platos/nuevo
    expect(await screen.findByRole('heading', { name: /nueva ficha técnica|nuevo plato/i })).toBeInTheDocument();

    // Mock successful draft creation and photo upload
    const createdDish = {
      ...mockDishDraftDetail,
      id: MOCK_DISH_ID_1,
      name: 'Lomo Saltado Criollo',
      category: 'Platos principales' as const,
      description: 'Clásico lomo con cebolla y tomate.',
    };
    mockedDishApi.createDishDraft.mockResolvedValueOnce(createdDish);
    mockedDishApi.uploadPhoto.mockResolvedValueOnce(createMockPhotoUploadResponse(MOCK_DISH_ID_1));

    // Fill form
    await user.type(screen.getByLabelText(/nombre del plato/i), 'Lomo Saltado Criollo');
    await user.selectOptions(screen.getByLabelText(/categoría/i), 'Platos principales');
    await user.type(screen.getByLabelText(/descripción/i), 'Clásico lomo con cebolla y tomate.');

    const photoFile = new File(['valid photo data'], 'lomo.jpg', { type: 'image/jpeg' });
    const fileInput = screen.getByLabelText(/fotografía/i, { selector: 'input[type="file"]' });
    fireEvent.change(fileInput, { target: { files: [photoFile] } });

    // Submit form
    const saveButton = screen.getByRole('button', { name: /guardar borrador|guardar/i });
    await user.click(saveButton);

    await waitFor(() => {
      expect(mockedDishApi.createDishDraft).toHaveBeenCalledWith({
        name: 'Lomo Saltado Criollo',
        category: 'Platos principales',
        description: 'Clásico lomo con cebolla y tomate.',
      });
      expect(mockedDishApi.uploadPhoto).toHaveBeenCalledWith(MOCK_DISH_ID_1, photoFile);
    });
  });
});
