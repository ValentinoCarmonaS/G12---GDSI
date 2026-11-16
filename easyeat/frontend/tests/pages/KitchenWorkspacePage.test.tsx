import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { KitchenWorkspacePage } from '@/pages/KitchenWorkspacePage';
import { authApi } from '@/api/authApi';
import { chefReviewFeedbackApi } from '@/api/chefReviewFeedbackApi';
import * as AuthModule from '@/hooks/useAuth';

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

  it('renders kitchen workspace with exclusive focus on KitchenKanbanBoard (US6, RF-019)', () => {
    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    // Kanban board is present with its two columns
    expect(screen.getByTestId('kds-kanban-section')).toBeInTheDocument();
    expect(screen.getByTestId('queued-column')).toBeInTheDocument();
    expect(screen.getByTestId('in-preparation-column')).toBeInTheDocument();

    // Placeholders and legacy controls are completely removed:
    // 1. Subtitle
    expect(screen.queryByText(/comandas en preparación.*despachos de cocina/i)).not.toBeInTheDocument();
    // 2. Global delay modifier controls
    expect(screen.queryByText(/ajuste de demora global de cocina/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /\+5 min/i })).not.toBeInTheDocument();
    // 3. Counter dispatch form by UUID
    expect(screen.queryByText(/despacho a mostrador/i)).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/uuid de la comanda/i)).not.toBeInTheDocument();
    // 4. Static ingredients card
    expect(screen.queryByText(/catálogo de ingredientes y materias primas/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /ver ingredientes →/i })).not.toBeInTheDocument();
    // 5. Four statistical status cards
    expect(screen.queryByText('En Cola')).not.toBeInTheDocument();
    expect(screen.queryByText('En Preparación')).not.toBeInTheDocument();
    expect(screen.queryByText('Para Retirar')).not.toBeInTheDocument();
    expect(screen.queryByText('Completadas hoy')).not.toBeInTheDocument();
  });

  it('invokes authApi.me on mount to validate token version and session liveness', () => {
    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    expect(authApi.me).toHaveBeenCalledTimes(1);
  });

  it('exposes navigation through the header and keeps no nested cards or metrics (C-08, US6, RF-019)', () => {
    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    // The dish-management card is no longer nested inside the KDS view...
    expect(screen.queryByText(/gestión de platos y fichas técnicas/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /gestionar platos →/i })).not.toBeInTheDocument();

    // ...and the ingredients card plus the four order-statistics cards are purged from page body.
    expect(screen.queryByText(/catálogo de ingredientes y materias primas/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /ver ingredientes →/i })).not.toBeInTheDocument();
    expect(screen.queryByText('En Cola')).not.toBeInTheDocument();
    expect(screen.queryByText('En Preparación')).not.toBeInTheDocument();
    expect(screen.queryByText('Para Retirar')).not.toBeInTheDocument();
    expect(screen.queryByText('Completadas hoy')).not.toBeInTheDocument();

    // Verify KitchenKanbanBoard is embedded into KitchenWorkspacePage
    expect(screen.getByTestId('kds-kanban-section')).toBeInTheDocument();
    expect(screen.getByTestId('queued-column')).toBeInTheDocument();
    expect(screen.getByTestId('in-preparation-column')).toBeInTheDocument();
  });

  describe('a draft returned by rejection (FR-020, FR-023, US6)', () => {
    it('renders no feedback banner on KDS page even if URL names a draft (relocated to dish management)', () => {
      render(
        <MemoryRouter initialEntries={['/cocina?dishId=dish-1']}>
          <KitchenWorkspacePage />
        </MemoryRouter>
      );

      expect(chefReviewFeedbackApi.getFeedback).not.toHaveBeenCalled();
      expect(screen.queryByTestId('rejection-feedback-banner')).not.toBeInTheDocument();
      expect(screen.queryByText(/Lucía Ferraro/)).not.toBeInTheDocument();
    });

    it('shows no acquisition cost, Food Cost or margin figure anywhere on the page', () => {
      const { container } = render(
        <MemoryRouter initialEntries={['/cocina?dishId=dish-1']}>
          <KitchenWorkspacePage />
        </MemoryRouter>
      );

      const amounts = container.querySelectorAll('[data-testid*="cost"], [data-testid*="price"], [data-testid*="margin"]');
      expect(amounts).toHaveLength(0);
    });
  });
});
