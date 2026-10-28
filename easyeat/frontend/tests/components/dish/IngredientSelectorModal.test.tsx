import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { IngredientSelectorModal } from '@/components/dish/IngredientSelectorModal';
import { inventoryApi } from '@/api/inventoryApi';
import type { ChefIngredientPageResponse } from '@/types/inventory';

vi.mock('@/api/inventoryApi', () => ({
  inventoryApi: {
    getChefCatalog: vi.fn(),
  },
}));

describe('IngredientSelectorModal Component (T038, CHF-2.2.2, FR-011, FR-012)', () => {
  const mockOnClose = vi.fn();
  const mockOnSelectIngredient = vi.fn();

  const mockCatalogResponse: ChefIngredientPageResponse = {
    items: [
      {
        id: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
        name: 'Arroz Carnaroli',
        category: 'Dry Goods',
        unitOfMeasure: 'g',
        stockStatus: 'IN_STOCK',
        currentStock: 5000,
      },
      {
        id: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02',
        name: 'Huevos de Campo',
        category: 'Dairy',
        unitOfMeasure: 'u',
        stockStatus: 'IN_STOCK',
        currentStock: 48,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    last: true,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(inventoryApi.getChefCatalog).mockResolvedValue(mockCatalogResponse);
  });

  it('renders modal with title, search input, category filter and ingredients table when open', async () => {
    render(
      <IngredientSelectorModal
        isOpen={true}
        onClose={mockOnClose}
        onSelectIngredient={mockOnSelectIngredient}
      />
    );

    expect(screen.getByText(/seleccionar ingrediente/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/buscar ingrediente/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/filtrar por categoría/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Arroz Carnaroli')).toBeInTheDocument();
      expect(screen.getByText('Huevos de Campo')).toBeInTheDocument();
    });
  });

  it('calls getChefCatalog with search query when typing in search input', async () => {
    const user = userEvent.setup();
    render(
      <IngredientSelectorModal
        isOpen={true}
        onClose={mockOnClose}
        onSelectIngredient={mockOnSelectIngredient}
      />
    );

    const searchInput = screen.getByPlaceholderText(/buscar ingrediente/i);
    await user.type(searchInput, 'Arroz');

    await waitFor(() => {
      expect(inventoryApi.getChefCatalog).toHaveBeenCalledWith(
        expect.objectContaining({
          search: 'Arroz',
        })
      );
    });
  });

  it('calls getChefCatalog with category filter when category dropdown changes', async () => {
    const user = userEvent.setup();
    render(
      <IngredientSelectorModal
        isOpen={true}
        onClose={mockOnClose}
        onSelectIngredient={mockOnSelectIngredient}
      />
    );

    const categorySelect = screen.getByLabelText(/filtrar por categoría/i);
    await user.selectOptions(categorySelect, 'Dry Goods');

    await waitFor(() => {
      expect(inventoryApi.getChefCatalog).toHaveBeenCalledWith(
        expect.objectContaining({
          category: 'Dry Goods',
        })
      );
    });
  });

  it('calls onSelectIngredient and closes modal when clicking an ingredient row', async () => {
    const user = userEvent.setup();
    render(
      <IngredientSelectorModal
        isOpen={true}
        onClose={mockOnClose}
        onSelectIngredient={mockOnSelectIngredient}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Arroz Carnaroli')).toBeInTheDocument();
    });

    const selectButtons = screen.getAllByRole('button', { name: /seleccionar/i });
    await user.click(selectButtons[0]);

    expect(mockOnSelectIngredient).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
        name: 'Arroz Carnaroli',
        unitOfMeasure: 'g',
      })
    );
  });

  it('indicates already added ingredients when selectedIngredientIds is passed', async () => {
    render(
      <IngredientSelectorModal
        isOpen={true}
        onClose={mockOnClose}
        onSelectIngredient={mockOnSelectIngredient}
        selectedIngredientIds={['c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01']}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Arroz Carnaroli')).toBeInTheDocument();
      expect(screen.getByText(/ya en la receta/i)).toBeInTheDocument();
    });
  });
});
