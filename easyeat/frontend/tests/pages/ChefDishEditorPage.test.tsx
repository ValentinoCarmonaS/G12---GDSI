import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ChefDishEditorPage } from '@/pages/ChefDishEditorPage';
import { dishApi } from '@/api/dishApi';
import { mockDishDraftDetail } from '../mocks/dishHandlers';

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
  BasicIdentityStep: ({ isReadOnly }: { isReadOnly: boolean }) => (
    <div data-testid="identity-step" data-read-only={String(isReadOnly)} />
  ),
}));

vi.mock('@/components/dish/RecipeBOMStep', () => ({
  RecipeBOMStep: ({ isReadOnly }: { isReadOnly: boolean }) => (
    <div data-testid="recipe-step" data-read-only={String(isReadOnly)} />
  ),
}));

vi.mock('@/components/dish/AllergenStep', () => ({
  AllergenStep: ({ isReadOnly }: { isReadOnly: boolean }) => (
    <div data-testid="allergen-step" data-read-only={String(isReadOnly)} />
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
});
