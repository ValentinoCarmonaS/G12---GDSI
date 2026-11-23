import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { persistUnifiedDishDraft } from '@/utils/chefDishPersistence';
import { ChefDishEditorPage } from '@/pages/ChefDishEditorPage';
import { dishApi } from '@/api/dishApi';
import { chefReviewFeedbackApi } from '@/api/chefReviewFeedbackApi';
import type { DishDetailResponse } from '@/types/dish';

vi.mock('@/api/dishApi', () => ({
  dishApi: {
    getDishById: vi.fn(),
    createDishDraft: vi.fn(),
    updateDish: vi.fn(),
    uploadPhoto: vi.fn(),
    deletePhoto: vi.fn(),
    updateRecipe: vi.fn(),
    updateAllergens: vi.fn(),
    submitForReview: vi.fn(),
  },
}));

vi.mock('@/api/chefReviewFeedbackApi', () => ({
  chefReviewFeedbackApi: {
    getDishReviewFeedback: vi.fn(),
  },
}));

vi.mock('@/api/ingredientApi', () => ({
  ingredientApi: {
    listIngredients: vi.fn().mockResolvedValue({
      content: [
        {
          id: 'ing-1',
          name: 'Tomate Perita',
          category: 'VEGETABLES',
          unit: 'kg',
          active: true,
        },
        {
          id: 'ing-2',
          name: 'Huevo de Campo',
          category: 'DAIRY',
          unit: 'u',
          active: true,
        },
      ],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 10,
    }),
  },
}));

const mockDishBase: DishDetailResponse = {
  id: 'dish-100',
  name: 'Milanesa Brutal',
  category: 'MAIN_COURSE',
  description: 'Con papas rotas',
  status: 'DRAFT',
  photoUrl: null,
  recipe: [
    {
      ingredientId: 'ing-1',
      ingredientName: 'Tomate Perita',
      quantity: 0.5,
      unit: 'kg',
    },
  ],
  restrictionCodes: ['CELIAC'],
  allergensConfirmed: true,
  createdAt: '2026-11-23T00:00:00Z',
  updatedAt: '2026-11-23T00:00:00Z',
};

function renderEditor(initialEntry = '/cocina/platos/nuevo') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/cocina/platos/nuevo" element={<ChefDishEditorPage />} />
        <Route path="/cocina/platos/:id" element={<ChefDishEditorPage />} />
        <Route path="/cocina/platos" element={<div data-testid="dishes-list">Lista</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('US4 — Editor guiado unificado Brutalism (T025)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(chefReviewFeedbackApi.getDishReviewFeedback).mockResolvedValue([]);
  });

  it('(1) orquesta persistUnifiedDishDraft secuencialmente y retiene persistedDishId ante fallo intermedio', async () => {
    vi.mocked(dishApi.createDishDraft).mockResolvedValueOnce({
      ...mockDishBase,
      id: 'dish-new-1',
    });
    vi.mocked(dishApi.updateRecipe).mockRejectedValueOnce(new Error('Fallo en receta'));

    await expect(
      persistUnifiedDishDraft(
        {
          persistedDishId: null,
          status: 'DRAFT',
          name: 'Milanesa Brutal',
          category: 'MAIN_COURSE',
          description: 'Con papas rotas',
          existingPhotoUrl: null,
          pendingPhotoFile: null,
          photoMarkedForDeletion: false,
          recipe: mockDishBase.recipe,
          restrictionCodes: ['CELIAC'],
          allergensConfirmed: true,
          isDirty: true,
        },
        'draft'
      )
    ).rejects.toMatchObject({
      persistedDishId: 'dish-new-1',
      failedStage: 'recipe',
    });

    expect(dishApi.createDishDraft).toHaveBeenCalledTimes(1);
    expect(dishApi.updateRecipe).toHaveBeenCalledWith('dish-new-1', {
      items: [{ ingredientId: 'ing-1', quantity: 0.5 }],
    });
  });

  it('(2) renderiza stepper numerado 01-04 (>= 48px) habilitado sin dishId previo y navega con Siguiente/Anterior', async () => {
    renderEditor('/cocina/platos/nuevo');

    const step01 = screen.getByRole('button', { name: /01/i });
    const step02 = screen.getByRole('button', { name: /02/i });
    const step03 = screen.getByRole('button', { name: /03/i });
    const step04 = screen.getByRole('button', { name: /04/i });

    expect(step01.className).toContain('min-h-[48px]');
    expect(step02).not.toBeDisabled();
    expect(step03).not.toBeDisabled();
    expect(step04).not.toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: /Siguiente/i }));
    expect(await screen.findByText(/El nombre del plato es obligatorio/i)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Nombre del plato/i), {
      target: { value: 'Tarta Brutal' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Siguiente/i }));

    expect(await screen.findByText(/Ingredientes de la receta/i)).toBeInTheDocument();
  });

  it('(3) usa BrutalistCard sin modo oscuro ni <table> en Paso 2 y expone Filtrar por categoría en IngredientSelectorModal', async () => {
    const { container } = renderEditor('/cocina/platos/nuevo');

    fireEvent.change(screen.getByLabelText(/Nombre del plato/i), {
      target: { value: 'Tarta Brutal' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Siguiente/i }));

    expect(container.querySelector('table')).toBeNull();

    fireEvent.click(await screen.findByRole('button', { name: /Agregar ingrediente/i }));
    expect(await screen.findByLabelText(/Filtrar por categoría/i)).toBeInTheDocument();
  });

  it('(4) muestra badge "Cambios sin guardar", registra beforeunload y abre BrutalistModal al volver con cambios', async () => {
    const addSpy = vi.spyOn(window, 'addEventListener');
    renderEditor('/cocina/platos/nuevo');

    fireEvent.change(screen.getByLabelText(/Nombre del plato/i), {
      target: { value: 'Cambio pendiente' },
    });

    expect(await screen.findByText('Cambios sin guardar')).toBeInTheDocument();
    expect(addSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function));

    fireEvent.click(screen.getByRole('link', { name: /Volver a Platos/i }));
    expect(await screen.findByText(/Salir sin guardar/i)).toBeInTheDocument();

    addSpy.mockRestore();
  });

  it('(5) respeta modo solo lectura en PUBLISHED y oculta datos financieros', async () => {
    vi.mocked(dishApi.getDishById).mockResolvedValueOnce({
      ...mockDishBase,
      status: 'PUBLISHED',
    });

    renderEditor('/cocina/platos/dish-100');

    expect(await screen.findByText(/Solo lectura/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Guardar borrador/i })).toBeNull();
    expect(screen.queryByText(/costo|margen|precio/i)).toBeNull();
  });
});
