import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ChefDishEditorPage } from '@/pages/ChefDishEditorPage';
import { dishApi } from '@/api/dishApi';
import { chefReviewFeedbackApi } from '@/api/chefReviewFeedbackApi';
import { mockDishDraftDetail, mockRecipeIngredients } from '../mocks/dishHandlers';

vi.mock('@/api/dishApi', () => ({
  dishApi: { getDishDetail: vi.fn() },
}));

vi.mock('@/api/chefReviewFeedbackApi', () => ({
  chefReviewFeedbackApi: { getFeedback: vi.fn() },
}));

vi.mock('@/components/common/WorkspaceLayout', () => ({
  WorkspaceLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/components/dish/BasicIdentityStep', () => ({
  BasicIdentityStep: ({
    isReadOnly,
    initialData,
  }: {
    isReadOnly: boolean;
    initialData?: Record<string, unknown>;
  }) => (
    <div
      data-testid="identity-step"
      data-read-only={String(isReadOnly)}
      data-initial={JSON.stringify(initialData ?? null)}
    />
  ),
}));

vi.mock('@/components/dish/RecipeBOMStep', () => ({
  RecipeBOMStep: ({
    isReadOnly,
    initialRecipe,
  }: {
    isReadOnly: boolean;
    initialRecipe?: unknown[];
  }) => (
    <div
      data-testid="recipe-step"
      data-read-only={String(isReadOnly)}
      data-initial={JSON.stringify(initialRecipe ?? [])}
    />
  ),
}));

vi.mock('@/components/dish/AllergenStep', () => ({
  AllergenStep: ({
    isReadOnly,
    initialRestrictions,
  }: {
    isReadOnly: boolean;
    initialRestrictions?: string[];
  }) => (
    <div
      data-testid="allergen-step"
      data-read-only={String(isReadOnly)}
      data-initial={JSON.stringify(initialRestrictions ?? [])}
    />
  ),
}));

vi.mock('@/components/dish/ReviewSubmitStep', () => ({
  ReviewSubmitStep: ({ isReadOnly }: { isReadOnly: boolean }) => (
    <div data-testid="review-step" data-read-only={String(isReadOnly)} />
  ),
}));

describe('ChefDishEditorPage', () => {
  it('keeps a cancelled dish read-only even when its editor URL is opened directly (FR-042)', async () => {
    const user = userEvent.setup();
    vi.mocked(dishApi.getDishDetail).mockResolvedValue({
      ...mockDishDraftDetail,
      status: 'CANCELLED',
    });

    render(
      <MemoryRouter initialEntries={['/cocina/platos/dish-1']}>
        <Routes>
          <Route path="/cocina/platos/:id" element={<ChefDishEditorPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByTestId('cancelled-dish-readonly')).toBeInTheDocument();
    expect(screen.getByTestId('identity-step')).toHaveAttribute('data-read-only', 'true');

    await user.click(screen.getByRole('button', { name: /receta \(bom\)/i }));
    expect(screen.getByTestId('recipe-step')).toHaveAttribute('data-read-only', 'true');

    await user.click(screen.getByRole('button', { name: /alérgenos/i }));
    expect(screen.getByTestId('allergen-step')).toHaveAttribute('data-read-only', 'true');

    await user.click(screen.getByRole('button', { name: /revisión y envío/i }));
    expect(screen.getByTestId('review-step')).toHaveAttribute('data-read-only', 'true');
  });

  it('preloads the Administrator’s proposal and previous-cycle comments for a returned draft (FR-042)', async () => {
    const user = userEvent.setup();
    const proposedIngredientId = mockRecipeIngredients[0].ingredientId;

    vi.mocked(dishApi.getDishDetail).mockResolvedValue(mockDishDraftDetail);
    vi.mocked(chefReviewFeedbackApi.getFeedback).mockResolvedValue({
      dishId: mockDishDraftDetail.id,
      entries: [
        {
          cycleNumber: 2,
          reviewerName: 'Lucía Ferraro',
          comment: 'Usá merluza en lugar de salmón.',
          proposedChanges: {
            title: 'Risotto de Merluza',
            description: 'Merluza fresca con hongos portobello.',
            allergenCodes: ['FISH'],
            dietaryLifestyleCodes: ['CELIAC'],
            ingredients: [{ ingredientId: proposedIngredientId, quantity: 300 }],
          },
          createdAt: '2026-10-30T10:00:00Z',
        },
      ],
    });

    render(
      <MemoryRouter initialEntries={['/cocina/platos/dish-1']}>
        <Routes>
          <Route path="/cocina/platos/:id" element={<ChefDishEditorPage />} />
        </Routes>
      </MemoryRouter>
    );

    // The previous cycle's comment is visible to the Chef.
    expect(await screen.findByText('Usá merluza en lugar de salmón.')).toBeInTheDocument();

    // Identity preloads the proposed title and description.
    await waitFor(() =>
      expect(screen.getByTestId('identity-step')).toHaveAttribute(
        'data-initial',
        expect.stringContaining('Risotto de Merluza')
      )
    );
    expect(screen.getByTestId('identity-step').getAttribute('data-initial')).toContain(
      'Merluza fresca con hongos portobello.'
    );

    // Recipe preloads the proposed quantity onto the existing ingredient line.
    await user.click(screen.getByRole('button', { name: /receta \(bom\)/i }));
    expect(screen.getByTestId('recipe-step').getAttribute('data-initial')).toContain(
      `"ingredientId":"${proposedIngredientId}","ingredientName":"Arroz Carnaroli","quantity":300`
    );

    // Allergen step merges the proposal's allergen and lifestyle codes.
    await user.click(screen.getByRole('button', { name: /alérgenos/i }));
    const restrictions = JSON.parse(
      screen.getByTestId('allergen-step').getAttribute('data-initial') ?? '[]'
    ) as string[];
    expect(restrictions).toEqual(['FISH', 'CELIAC']);

    // No cost, margin or price reaches the Chef-facing view.
    expect(
      screen.queryByText(/costo|margen|food cost|precio/i)
    ).not.toBeInTheDocument();
  });
});
