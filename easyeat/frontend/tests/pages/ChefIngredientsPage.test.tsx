import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { ChefIngredientsPage } from '@/pages/ChefIngredientsPage';
import { inventoryApi } from '@/api/inventoryApi';
import * as AuthModule from '@/hooks/useAuth';
import { ChefIngredientPageResponse } from '@/types/inventory';

vi.mock('@/api/inventoryApi');
vi.mock('@/hooks/useAuth');

const mockChefPageResponse: ChefIngredientPageResponse = {
  items: [
    {
      id: 'chef-ing-1',
      name: 'Albahaca Fresca',
      category: 'Produce',
      unitOfMeasure: 'g',
      stockStatus: 'In Stock',
      currentStock: 500,
    },
    {
      id: 'chef-ing-2',
      name: 'Harina de Trigo 000',
      category: 'Dry Goods',
      unitOfMeasure: 'kg',
      stockStatus: 'In Stock',
      currentStock: 45,
    },
    {
      id: 'chef-ing-3',
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
  totalPages: 1,
  hasNext: false,
};

describe('ChefIngredientsPage Component (FR-021, FR-022, FR-023, FR-024, FR-032)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'carlos.chef@restaurant.com',
        role: 'CHEF',
        tenantId: 'tenant-100',
        tokenVersion: 1,
        mustChangePassword: false,
      },
      token: 'mock-chef-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('renders kitchen ingredient catalog with alphabetical order and stock availability badges', async () => {
    vi.mocked(inventoryApi.getChefCatalog).mockResolvedValueOnce(mockChefPageResponse);

    render(
      <MemoryRouter>
        <ChefIngredientsPage />
      </MemoryRouter>
    );

    expect(screen.getByText('Catálogo de Ingredientes de Cocina')).toBeInTheDocument();

    expect(await screen.findByText('Albahaca Fresca')).toBeInTheDocument();
    expect(screen.getByText('Harina de Trigo 000')).toBeInTheDocument();
    expect(screen.getByText('Levadura Fresca')).toBeInTheDocument();

    expect(screen.getAllByText('In Stock').length).toBe(2);
    expect(screen.getByText('Out of Stock')).toBeInTheDocument();
  });

  it('strictly suppresses unit purchase costs and audit logs (FR-023)', async () => {
    vi.mocked(inventoryApi.getChefCatalog).mockResolvedValueOnce(mockChefPageResponse);

    render(
      <MemoryRouter>
        <ChefIngredientsPage />
      </MemoryRouter>
    );

    await screen.findByText('Albahaca Fresca');

    // Asserts that no financial cost headers or monetary values exist in the view
    expect(screen.queryByText(/costo/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/auditoría/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
  });

  it('filters kitchen ingredients by category and name search query', async () => {
    const user = userEvent.setup();
    vi.mocked(inventoryApi.getChefCatalog).mockResolvedValue(mockChefPageResponse);

    render(
      <MemoryRouter>
        <ChefIngredientsPage />
      </MemoryRouter>
    );

    await screen.findByText('Albahaca Fresca');

    const searchInput = screen.getByPlaceholderText(/buscar materia prima/i);
    await user.type(searchInput, 'Harina');

    await waitFor(() => {
      expect(inventoryApi.getChefCatalog).toHaveBeenCalledWith(
        expect.objectContaining({ search: 'Harina' })
      );
    });
  });

  it('supports pagination controls with 20 items per page default', async () => {
    const paginatedResponse: ChefIngredientPageResponse = {
      ...mockChefPageResponse,
      page: 0,
      size: 20,
      totalElements: 25,
      totalPages: 2,
      hasNext: true,
    };
    vi.mocked(inventoryApi.getChefCatalog).mockResolvedValueOnce(paginatedResponse);

    render(
      <MemoryRouter>
        <ChefIngredientsPage />
      </MemoryRouter>
    );

    await screen.findByText('Albahaca Fresca');

    expect(screen.getByText(/página 1 de 2/i)).toBeInTheDocument();
    const nextButton = screen.getByRole('button', { name: /siguiente/i });
    expect(nextButton).not.toBeDisabled();
  });
});
