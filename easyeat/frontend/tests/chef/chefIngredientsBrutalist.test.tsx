import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ChefIngredientsPage } from '@/pages/ChefIngredientsPage';
import { inventoryApi } from '@/api/inventoryApi';
import * as AuthModule from '@/hooks/useAuth';
import { ChefIngredientPageResponse } from '@/types/inventory';

vi.mock('@/api/inventoryApi');
vi.mock('@/hooks/useAuth');

const mockResponse: ChefIngredientPageResponse = {
  items: [
    {
      id: 'ing-1',
      name: 'Albahaca Fresca',
      category: 'Produce',
      unitOfMeasure: 'g',
      stockStatus: 'In Stock',
      currentStock: 500,
    },
    {
      id: 'ing-2',
      name: 'Harina 000',
      category: 'Dry Goods',
      unitOfMeasure: 'kg',
      stockStatus: 'In Stock',
      currentStock: 25,
    },
    {
      id: 'ing-3',
      name: 'Levadura Fresca',
      category: 'Dry Goods',
      unitOfMeasure: 'g',
      stockStatus: 'Out of Stock',
      currentStock: 0,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 3,
  totalPages: 2,
  hasNext: true,
};

describe('US5 — Catálogo de ingredientes de cocina en tarjetas Brutalism', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'chef@easyeat.com',
        role: 'CHEF',
        tenantId: 'tenant-1',
        tokenVersion: 1,
        mustChangePassword: false,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('muestra enlace táctil al KDS, buscador y filtro >= 48px, y paginación Brutalism', async () => {
    const user = userEvent.setup();
    vi.mocked(inventoryApi.getChefCatalog).mockResolvedValue(mockResponse);

    render(
      <MemoryRouter>
        <ChefIngredientsPage />
      </MemoryRouter>
    );

    const backLink = screen.getByRole('link', { name: /Volver al Panel KDS de Cocina/i });
    expect(backLink).toHaveAttribute('href', '/cocina');
    expect(backLink.className).toContain('min-h-[48px]');

    const searchInput = screen.getByPlaceholderText('Buscar materia prima...');
    expect(searchInput.className).toContain('min-h-[48px]');

    const categorySelect = screen.getByLabelText(/Categoría/i);
    expect(categorySelect.className).toContain('min-h-[48px]');
    expect(screen.getByRole('option', { name: 'Todas' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Produce' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Meat' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Dairy' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Dry Goods' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Spices' })).toBeInTheDocument();

    await screen.findByText('Albahaca Fresca');

    const prevBtn = screen.getByRole('button', { name: /Anterior/i });
    const nextBtn = screen.getByRole('button', { name: /Siguiente/i });
    expect(prevBtn).toBeDisabled();
    expect(nextBtn).toBeEnabled();

    await user.click(nextBtn);
    await waitFor(() => {
      expect(inventoryApi.getChefCatalog).toHaveBeenCalledWith(
        expect.objectContaining({ page: 1 })
      );
    });
  });

  it('renderiza grilla responsiva de BrutalistCard sin <table>, con stock en JetBrains Mono y énfasis alert-danger en Out of Stock', async () => {
    vi.mocked(inventoryApi.getChefCatalog).mockResolvedValueOnce(mockResponse);

    const { container } = render(
      <MemoryRouter>
        <ChefIngredientsPage />
      </MemoryRouter>
    );

    await screen.findByText('Albahaca Fresca');

    // Cero tablas
    expect(container.querySelector('table')).toBeNull();

    // Grilla responsiva 1 / 2 / 3 columnas
    const grid = screen.getByTestId('chef-ingredients-grid');
    expect(grid.className).toContain('grid-cols-1');
    expect(grid.className).toContain('md:grid-cols-2');
    expect(grid.className).toContain('lg:grid-cols-3');

    // Stock en JetBrains Mono
    const stockIn = screen.getByText('500 g');
    expect(stockIn.className).toContain('font-ee-mono');

    // Énfasis alert-danger en Out of Stock
    const outCard = screen.getByTestId('ingredient-card-ing-3');
    expect(outCard.className).toContain('border-ee-danger-border');
    expect(outCard.className).toContain('bg-ee-danger-bg');

    const badges = screen.getAllByTestId('status-badge');
    const outBadge = badges.find((b) => b.textContent?.includes('Out of Stock'));
    expect(outBadge).toBeDefined();
    expect(outBadge?.querySelector('svg')).not.toBeNull();
  });

  it('muestra LoadingSkeleton, EmptyState y FeedbackState ante error', async () => {
    // 1. LoadingSkeleton
    let resolvePromise!: (val: ChefIngredientPageResponse) => void;
    vi.mocked(inventoryApi.getChefCatalog).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolvePromise = resolve;
        })
    );

    const { unmount } = render(
      <MemoryRouter>
        <ChefIngredientsPage />
      </MemoryRouter>
    );

    expect(screen.getAllByTestId('loading-skeleton').length).toBeGreaterThan(0);
    resolvePromise({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 1, hasNext: false });

    // 2. EmptyState
    expect(await screen.findByTestId('empty-state')).toBeInTheDocument();
    unmount();

    // 3. FeedbackState ante error
    vi.mocked(inventoryApi.getChefCatalog).mockRejectedValueOnce(new Error('Network error'));
    render(
      <MemoryRouter>
        <ChefIngredientsPage />
      </MemoryRouter>
    );

    const feedback = await screen.findByTestId('feedback-state');
    expect(feedback).toHaveTextContent(/Error al consultar el catálogo/i);
  });

  it('mantiene aislamiento financiero absoluto (FR-023)', async () => {
    vi.mocked(inventoryApi.getChefCatalog).mockResolvedValueOnce(mockResponse);

    render(
      <MemoryRouter>
        <ChefIngredientsPage />
      </MemoryRouter>
    );

    await screen.findByText('Albahaca Fresca');

    expect(screen.queryByText(/costo/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/precio/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/margen/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/auditoría/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
  });
});
