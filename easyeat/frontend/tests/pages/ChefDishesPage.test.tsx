import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ChefDishesPage } from '@/pages/ChefDishesPage';
import { dishApi } from '@/api/dishApi';
import {
  mockDishDraftSummary,
  mockDishPendingPriceSummary,
  createMockDishPage,
} from '../mocks/dishHandlers';

vi.mock('@/api/dishApi', () => ({
  dishApi: {
    listDishes: vi.fn(),
    deleteDish: vi.fn(),
  },
}));

describe('ChefDishesPage Component (T024, CHF-2.2.1, CHF-2.2.4)', () => {
  const mockedDishApi = vi.mocked(dishApi);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderComponent = () =>
    render(
      <MemoryRouter>
        <ChefDishesPage />
      </MemoryRouter>
    );

  it('renders page layout, title, and "Nuevo Plato" action button on mount', async () => {
    mockedDishApi.listDishes.mockResolvedValueOnce(createMockDishPage([]));

    renderComponent();

    expect(screen.getByRole('heading', { name: /gestión de platos/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /nuevo plato/i })).toHaveAttribute(
      'href',
      '/cocina/platos/nuevo'
    );
  });

  it('queries dishApi.listDishes on mount with default 20 items per page and renders dishes table', async () => {
    mockedDishApi.listDishes.mockResolvedValueOnce(
      createMockDishPage([mockDishDraftSummary, mockDishPendingPriceSummary])
    );

    renderComponent();

    await waitFor(() => {
      expect(mockedDishApi.listDishes).toHaveBeenCalledWith({
        status: 'ALL',
        page: 0,
        size: 20,
      });
    });

    expect(screen.getByText('Risotto de Hongos Portobello')).toBeInTheDocument();
    expect(screen.getByText('Pad Thai con Maní')).toBeInTheDocument();
    expect(screen.getAllByText('Platos principales')).toHaveLength(2);
    expect(screen.getByText(/borrador/i)).toBeInTheDocument();
    expect(screen.getByText(/pendiente de precio/i)).toBeInTheDocument();
  });

  it('re-fetches dishes when status filter dropdown selection changes', async () => {
    const user = userEvent.setup();
    mockedDishApi.listDishes
      .mockResolvedValueOnce(createMockDishPage([mockDishDraftSummary, mockDishPendingPriceSummary]))
      .mockResolvedValueOnce(createMockDishPage([mockDishDraftSummary]));

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Risotto de Hongos Portobello')).toBeInTheDocument();
    });

    const statusFilter = screen.getByLabelText(/filtrar por estado/i);
    await user.selectOptions(statusFilter, 'DRAFT');

    await waitFor(() => {
      expect(mockedDishApi.listDishes).toHaveBeenCalledWith({
        status: 'DRAFT',
        page: 0,
        size: 20,
      });
    });
  });

  it('calls dishApi.deleteDish when Chef confirms deletion of a draft dish', async () => {
    const user = userEvent.setup();
    window.confirm = vi.fn().mockReturnValue(true);

    mockedDishApi.listDishes
      .mockResolvedValueOnce(createMockDishPage([mockDishDraftSummary]))
      .mockResolvedValueOnce(createMockDishPage([]));
    mockedDishApi.deleteDish.mockResolvedValueOnce(undefined);

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Risotto de Hongos Portobello')).toBeInTheDocument();
    });

    const deleteButton = screen.getByRole('button', { name: /eliminar/i });
    await user.click(deleteButton);

    expect(window.confirm).toHaveBeenCalled();
    expect(mockedDishApi.deleteDish).toHaveBeenCalledWith(mockDishDraftSummary.id);
  });

  it('does not display delete button for dishes in PENDING_PRICE status', async () => {
    mockedDishApi.listDishes.mockResolvedValueOnce(
      createMockDishPage([mockDishPendingPriceSummary])
    );

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Pad Thai con Maní')).toBeInTheDocument();
    });

    expect(screen.queryByRole('button', { name: /eliminar/i })).not.toBeInTheDocument();
  });

  it('renders cancelled dishes as read-only and exposes no edit or delete action (FR-042)', async () => {
    mockedDishApi.listDishes.mockResolvedValueOnce(
      createMockDishPage([{ ...mockDishPendingPriceSummary, status: 'CANCELLED' }])
    );

    renderComponent();

    expect(await screen.findByText('Pad Thai con Maní')).toBeInTheDocument();
    expect(screen.getByText('Cancelado')).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Pad Thai con Maní' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /editar/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /eliminar/i })).not.toBeInTheDocument();
  });

  it('renders informative empty state when no dishes are returned', async () => {
    mockedDishApi.listDishes.mockResolvedValueOnce(createMockDishPage([]));

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText(/no hay platos registrados/i)).toBeInTheDocument();
    });
  });
});
