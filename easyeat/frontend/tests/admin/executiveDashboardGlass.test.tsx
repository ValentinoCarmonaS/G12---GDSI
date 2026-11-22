import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminPendingDishesProvider } from '../../src/context/AdminPendingDishesContext';
import { ThemeScopeProvider } from '../../src/theme/ThemeScopeProvider';
import { AdminWorkspacePage } from '../../src/pages/AdminWorkspacePage';
import { FinancialHealthChart } from '../../src/components/analytics/FinancialHealthChart';
import { DishRankingTable } from '../../src/components/analytics/DishRankingTable';
import { authApi } from '../../src/api/authApi';
import { getFinancialHealth, getDishRanking } from '../../src/api/analyticsApi';
import { dishReviewApi } from '../../src/api/dishReviewApi';
import App from '../../src/App';

vi.mock('../../src/api/authApi', () => ({
  authApi: {
    me: vi.fn(),
    logout: vi.fn(),
  },
}));

vi.mock('../../src/api/analyticsApi', () => ({
  getFinancialHealth: vi.fn(),
  getDishRanking: vi.fn(),
}));

vi.mock('../../src/api/dishReviewApi', () => ({
  dishReviewApi: {
    getInbox: vi.fn(),
  },
}));

vi.mock('../../src/api/adminNotificationApi', () => ({
  adminNotificationApi: {
    listNotifications: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  },
}));

vi.mock('../../src/hooks/useAuth', () => ({
  useAuth: () => ({
    user: {
      id: 1,
      email: 'admin@easyeat.com',
      fullName: 'Admin Ejecutivo',
      role: 'ADMINISTRADOR',
      tenantId: '42',
      tenantName: 'Bistró Central',
    },
    isAuthenticated: true,
    isLoading: false,
    logout: vi.fn(),
  }),
}));

const renderWithProviders = (ui: React.ReactElement, initialEntries = ['/admin']) =>
  render(
    <MemoryRouter initialEntries={initialEntries}>
      <ThemeScopeProvider>
        <AdminPendingDishesProvider>{ui}</AdminPendingDishesProvider>
      </ThemeScopeProvider>
    </MemoryRouter>
  );

describe('Phase 4 US2 — Executive Dashboard Glassmorphism & Analytics', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(authApi.me).mockResolvedValue({
      id: 1,
      email: 'admin@easyeat.com',
      fullName: 'Admin Ejecutivo',
      role: 'ADMINISTRADOR',
      tenantId: '42',
    } as never);
    vi.mocked(dishReviewApi.getInbox).mockResolvedValue({
      content: [],
      pendingCount: 3,
      totalElements: 3,
      totalPages: 1,
      number: 0,
      size: 1,
    } as never);
    vi.mocked(getFinancialHealth).mockResolvedValue({
      granularity: 'day',
      periodStart: '2026-11-22T00:00:00Z',
      periodEnd: '2026-11-22T23:59:59Z',
      revenue: 125400.5,
      orderCount: 48,
    });
    vi.mocked(getDishRanking).mockResolvedValue([
      { dishId: '1', dishName: 'Milanesa Napolitana', unitsSold: 32 },
      { dishId: '2', dishName: 'Risotto de Hongos', unitsSold: 19 },
    ]);
  });

  it('redirects /admin/analytics cleanly to /admin without legacy dark container', async () => {
    window.history.pushState({}, '', '/admin/analytics');
    const { container } = render(<App />);

    await waitFor(() => {
      expect(window.location.pathname).toBe('/admin');
    });
    expect(container.querySelector('.legacy-dark')).toBeNull();
  });

  it('validates session on mount, removes placeholder/purple classes, and renders KPI band + 2-column grid', async () => {
    const { container } = renderWithProviders(<AdminWorkspacePage />);

    await waitFor(() => {
      expect(authApi.me).toHaveBeenCalledTimes(1);
    });

    expect(screen.queryByText(/Ámbito de Administración Multi-Tenant/i)).toBeNull();
    expect(screen.queryByText(/Personal Activo/i)).toBeNull();
    expect(container.innerHTML).not.toMatch(/text-purple-600|bg-purple-50|legacy-dark/);

    const grid = container.querySelector('[data-testid="executive-dashboard-grid"]');
    expect(grid).not.toBeNull();
    expect(grid?.className).toContain('lg:grid-cols-[minmax(0,1.65fr)_minmax(0,1fr)]');

    await waitFor(() => {
      expect(screen.getByText(/48/)).toBeInTheDocument();
    });
    const kpiValue = screen.getByTestId('financial-kpi-orders');
    expect(kpiValue.className).toContain('font-ee-mono');
    expect(kpiValue.className).toContain('tabular-nums');
  });

  it('toggles FinancialHealthChart between chart and accessible semantic table using dataViz tokens', () => {
    renderWithProviders(
      <FinancialHealthChart
        data={{
          granularity: 'day',
          periodStart: '2026-11-22T00:00:00Z',
          periodEnd: '2026-11-22T23:59:59Z',
          revenue: 125400.5,
          orderCount: 48,
        }}
      />
    );

    const toggleBtn = screen.getByRole('button', { name: /Ver tabla/i });
    fireEvent.click(toggleBtn);

    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Ver gráfico/i })).toBeInTheDocument();
  });

  it('renders DishRankingTable as GlassCard items with JetBrains Mono figures and independent period tabs', async () => {
    renderWithProviders(<DishRankingTable />);

    await waitFor(() => {
      expect(screen.getByText('Milanesa Napolitana')).toBeInTheDocument();
    });

    const unitsEl = screen.getByText('32');
    expect(unitsEl.className).toContain('font-ee-mono');
    expect(unitsEl.className).toContain('tabular-nums');

    const weekTab = screen.getByRole('tab', { name: 'Semana' });
    fireEvent.click(weekTab);

    await waitFor(() => {
      expect(getDishRanking).toHaveBeenCalledWith('week');
    });
  });

  it('renders LoadingSkeleton, EmptyState, and FeedbackState with retry on error', async () => {
    vi.mocked(getDishRanking).mockRejectedValueOnce(new Error('Network error'));

    renderWithProviders(<DishRankingTable />);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    const retryBtn = screen.getByRole('button', { name: /Reintentar/i });
    vi.mocked(getDishRanking).mockResolvedValueOnce([]);
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByText(/No hay ventas registradas/i)).toBeInTheDocument();
    });
  });
});
