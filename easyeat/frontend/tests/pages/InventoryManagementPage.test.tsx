import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { InventoryManagementPage } from '@/pages/InventoryManagementPage';
import { inventoryApi } from '@/api/inventoryApi';
import * as AuthModule from '@/hooks/useAuth';
import { AdminIngredientDetailResponse } from '@/types/inventory';

vi.mock('@/api/inventoryApi');
vi.mock('@/hooks/useAuth');

const mockIngredients: AdminIngredientDetailResponse[] = [
  {
    id: 'ing-1',
    tenantId: 'tenant-100',
    name: 'Harina 000',
    category: 'Dry Goods',
    unitOfMeasure: 'kg',
    unitPurchaseCost: 150.0,
    status: 'ACTIVE',
    currentStock: 40.0,
    createdAt: '2026-10-24T10:00:00Z',
    updatedAt: '2026-10-24T10:00:00Z',
  },
  {
    id: 'ing-2',
    tenantId: 'tenant-100',
    name: 'Tomates Perita',
    category: 'Produce',
    unitOfMeasure: 'kg',
    unitPurchaseCost: 320.0,
    status: 'DISCONTINUED',
    currentStock: 0,
    createdAt: '2026-10-24T11:00:00Z',
    updatedAt: '2026-10-24T11:00:00Z',
  },
  {
    id: 'ing-3',
    tenantId: 'tenant-100',
    name: 'Leche Entera',
    category: 'Dairy',
    unitOfMeasure: 'l',
    unitPurchaseCost: 90.0,
    status: 'ARCHIVED',
    currentStock: 0,
    createdAt: '2026-10-24T12:00:00Z',
    updatedAt: '2026-10-24T12:00:00Z',
  },
];

describe('InventoryManagementPage Component (FR-010, FR-012, FR-014, FR-031)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'admin-1',
        email: 'admin@restaurant.com',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-100',
        tokenVersion: 1,
        mustChangePassword: false,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('loads and displays master ingredients in the catalog table', async () => {
    vi.mocked(inventoryApi.listIngredients).mockResolvedValueOnce(mockIngredients);

    render(
      <MemoryRouter>
        <InventoryManagementPage />
      </MemoryRouter>
    );

    expect(screen.getByText('Inventario y Materias Primas')).toBeInTheDocument();
    expect(await screen.findByText('Harina 000')).toBeInTheDocument();
    expect(screen.getByText('Tomates Perita')).toBeInTheDocument();
    expect(screen.getByText('Leche Entera')).toBeInTheDocument();
    expect(screen.getByText('$150.00')).toBeInTheDocument();
  });

  it('filters ingredients by name search query', async () => {
    const user = userEvent.setup();
    vi.mocked(inventoryApi.listIngredients).mockResolvedValueOnce(mockIngredients);

    render(
      <MemoryRouter>
        <InventoryManagementPage />
      </MemoryRouter>
    );

    await screen.findByText('Harina 000');

    const searchInput = screen.getByPlaceholderText(/buscar por nombre/i);
    await user.type(searchInput, 'Tomates');

    expect(screen.getByText('Tomates Perita')).toBeInTheDocument();
    expect(screen.queryByText('Harina 000')).not.toBeInTheDocument();
    expect(screen.queryByText('Leche Entera')).not.toBeInTheDocument();
  });

  it('registers a new ingredient via modal', async () => {
    const user = userEvent.setup();
    vi.mocked(inventoryApi.listIngredients).mockResolvedValue(mockIngredients);
    vi.mocked(inventoryApi.createIngredient).mockResolvedValueOnce({
      id: 'ing-4',
      tenantId: 'tenant-100',
      name: 'Queso Mozzarella',
      category: 'Dairy',
      unitOfMeasure: 'kg',
      unitPurchaseCost: 800.0,
      status: 'ACTIVE',
      currentStock: 0,
      createdAt: '2026-10-24T14:00:00Z',
      updatedAt: '2026-10-24T14:00:00Z',
    });

    render(
      <MemoryRouter>
        <InventoryManagementPage />
      </MemoryRouter>
    );

    await screen.findByText('Harina 000');

    await user.click(screen.getByRole('button', { name: /nuevo ingrediente/i }));

    expect(await screen.findByRole('heading', { name: /nuevo ingrediente/i })).toBeInTheDocument();

    await user.type(screen.getByLabelText(/nombre del ingrediente/i), 'Queso Mozzarella');
    await user.selectOptions(screen.getByLabelText(/^categoría$/i), 'Dairy');
    await user.selectOptions(screen.getByLabelText(/unidad de medida/i), 'kg');
    await user.type(screen.getByLabelText(/costo unitario/i), '800');

    await user.click(screen.getByRole('button', { name: /guardar ingrediente/i }));

    await waitFor(() => {
      expect(inventoryApi.createIngredient).toHaveBeenCalledWith({
        name: 'Queso Mozzarella',
        category: 'Dairy',
        unitOfMeasure: 'kg',
        unitPurchaseCost: 800,
      });
    });
  });

  it('transitions ingredient status to DISCONTINUED (FR-014)', async () => {
    const user = userEvent.setup();
    vi.mocked(inventoryApi.listIngredients).mockResolvedValue(mockIngredients);
    vi.mocked(inventoryApi.updateStatus).mockResolvedValueOnce({
      ...mockIngredients[0],
      status: 'DISCONTINUED',
    });

    render(
      <MemoryRouter>
        <InventoryManagementPage />
      </MemoryRouter>
    );

    await screen.findByText('Harina 000');

    const discontinueButtons = screen.getAllByRole('button', { name: /discontinuar/i });
    await user.click(discontinueButtons[0]);

    await waitFor(() => {
      expect(inventoryApi.updateStatus).toHaveBeenCalledWith('ing-1', 'DISCONTINUED');
    });
  });

  it('disables archive action for ingredients with currentStock > 0 (FR-014)', async () => {
    vi.mocked(inventoryApi.listIngredients).mockResolvedValueOnce(mockIngredients);

    render(
      <MemoryRouter>
        <InventoryManagementPage />
      </MemoryRouter>
    );

    await screen.findByText('Harina 000');

    // Harina 000 has 40.0 stock, so its archive button must be disabled
    const archiveButtons = screen.getAllByRole('button', { name: /archivar/i });
    expect(archiveButtons[0]).toBeDisabled();
    expect(archiveButtons[0]).toHaveAttribute('title', expect.stringMatching(/no se puede archivar con stock/i));
  });

  it('allows archive action for ingredient with currentStock == 0', async () => {
    const user = userEvent.setup();
    vi.mocked(inventoryApi.listIngredients).mockResolvedValue(mockIngredients);
    vi.mocked(inventoryApi.updateStatus).mockResolvedValueOnce({
      ...mockIngredients[1],
      status: 'ARCHIVED',
    });

    render(
      <MemoryRouter>
        <InventoryManagementPage />
      </MemoryRouter>
    );

    await screen.findByText('Tomates Perita');

    const archiveButtons = screen.getAllByRole('button', { name: /archivar/i });
    // Tomates Perita is second row and has 0 stock
    expect(archiveButtons[1]).not.toBeDisabled();
    await user.click(archiveButtons[1]);

    await waitFor(() => {
      expect(inventoryApi.updateStatus).toHaveBeenCalledWith('ing-2', 'ARCHIVED');
    });
  });
});
