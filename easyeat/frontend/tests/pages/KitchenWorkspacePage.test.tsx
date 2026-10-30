import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { KitchenWorkspacePage } from '@/pages/KitchenWorkspacePage';
import { authApi } from '@/api/authApi';
import { chefReviewFeedbackApi } from '@/api/chefReviewFeedbackApi';
import * as AuthModule from '@/hooks/useAuth';
import type { ChefReviewFeedback } from '@/types/dishReview';

vi.mock('@/hooks/useAuth');
vi.mock('@/api/chefReviewFeedbackApi', () => ({
  chefReviewFeedbackApi: { getFeedback: vi.fn() },
}));

describe('KitchenWorkspacePage (T029, CHF-1.2.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.mocked(chefReviewFeedbackApi.getFeedback).mockResolvedValue({
      dishId: 'dish-1',
      entries: [],
    });
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-user-1',
        email: 'carlos.chef@comedorcentral.com',
        role: 'CHEF',
        tenantId: 'tenant-comedor-1',
        tokenVersion: 1,
      },
      token: 'jwt-chef-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('renders kitchen workspace layout with KDS title and order status cards (T029)', () => {
    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    expect(screen.getByText(/kitchen display system/i)).toBeInTheDocument();
    expect(screen.getByText(/comandas en preparación y despachos de cocina/i)).toBeInTheDocument();
    expect(screen.getByText('En Cola')).toBeInTheDocument();
    expect(screen.getByText('En Preparación')).toBeInTheDocument();
    expect(screen.getByText('Para Retirar')).toBeInTheDocument();
    expect(screen.getByText('Completadas hoy')).toBeInTheDocument();
  });

  it('invokes authApi.me on mount to validate token version and session liveness', () => {
    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    expect(authApi.me).toHaveBeenCalledTimes(1);
  });

  describe('a draft returned by rejection (FR-020, FR-023)', () => {
    const feedback: ChefReviewFeedback = {
      dishId: 'dish-1',
      entries: [
        {
          cycleNumber: 1,
          reviewerName: 'Lucía Ferraro',
          comment: 'El costo del salmón dispara el food cost. Usá merluza y reenviá la ficha.',
          createdAt: '2026-10-29T10:00:00Z',
        },
      ],
    };

    it('renders the feedback banner when the URL names the returned draft', async () => {
      vi.mocked(chefReviewFeedbackApi.getFeedback).mockResolvedValue(feedback);

      render(
        <MemoryRouter initialEntries={['/cocina?dishId=dish-1']}>
          <KitchenWorkspacePage />
        </MemoryRouter>
      );

      await waitFor(() =>
        expect(screen.getByText(feedback.entries[0].comment as string)).toBeInTheDocument()
      );
      expect(screen.getByText(/Lucía Ferraro/)).toBeInTheDocument();
    });

    it('renders no banner when the URL names no draft', () => {
      render(
        <MemoryRouter>
          <KitchenWorkspacePage />
        </MemoryRouter>
      );

      expect(chefReviewFeedbackApi.getFeedback).not.toHaveBeenCalled();
      expect(screen.queryByTestId('rejection-feedback-banner')).not.toBeInTheDocument();
    });

    it('shows no acquisition cost, Food Cost or margin figure anywhere on the page', async () => {
      vi.mocked(chefReviewFeedbackApi.getFeedback).mockResolvedValue(feedback);

      const { container } = render(
        <MemoryRouter initialEntries={['/cocina?dishId=dish-1']}>
          <KitchenWorkspacePage />
        </MemoryRouter>
      );

      await waitFor(() =>
        expect(screen.getByText(feedback.entries[0].comment as string)).toBeInTheDocument()
      );

      // The feedback comment legitimately mentions "costo"/"food cost" in prose — that is the
      // Administrator's own words, not a financial field this page renders. The structural
      // guarantee is that no element carries a numeric financial value, which this page has no
      // way to render in the first place: it holds no dish price, margin or cost state at all.
      const amounts = container.querySelectorAll('[data-testid*="cost"], [data-testid*="price"], [data-testid*="margin"]');
      expect(amounts).toHaveLength(0);
    });
  });
});
