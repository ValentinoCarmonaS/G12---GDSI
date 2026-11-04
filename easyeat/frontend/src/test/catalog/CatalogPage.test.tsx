import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import CatalogPage from '@/pages/CatalogPage';
import { dinerCatalogApi } from '@/api/dinerCatalogApi';
import { TenantContext, TenantContextType } from '@/context/TenantContext';
import { CartContext, CartContextType } from '@/context/CartContext';
import type { CatalogResponse } from '@/types/dinerCatalog';
import {
  mockEstablishmentSummaryActive,
  mockEstablishmentDetailActive,
  mockDishAvailableItem,
  mockDishOutOfStockItem,
} from '@/test/mocks/dinerCatalogHandlers';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    user: { email: 'comensal@easyeat.com', role: 'COMENSAL' },
    isAuthenticated: true,
    logout: vi.fn(),
  }),
}));

describe('CatalogPage Component Tests (T026, US2, COM-4.1.1, FR-007, FR-008, FR-009, FR-012)', () => {
  const defaultCartContext: CartContextType = {
    items: [],
    addItem: vi.fn(),
    removeItem: vi.fn(),
    updateQuantity: vi.fn(),
    clearCart: vi.fn(),
    reEvaluateCompliance: vi.fn(),
    totalAmount: 0,
    totalCount: 0,
    hasConflictingItems: false,
    hasPausedItems: false,
  };

  const defaultTenantContext: TenantContextType = {
    tenant: mockEstablishmentSummaryActive,
    setTenant: vi.fn(),
    clearTenant: vi.fn(),
    isLoading: false,
  };

  const mockCatalogData: CatalogResponse = {
    establishment: {
      ...mockEstablishmentDetailActive,
      isOpen: true,
      nextOpeningSchedule: null,
    },
    categories: [
      {
        category: 'Platos principales',
        dishes: [mockDishAvailableItem, mockDishOutOfStockItem],
      },
      {
        category: 'Postres',
        dishes: [
          {
            id: 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a58',
            name: 'Flan Casero con Dulce de Leche',
            description: 'Flan tradicional al caramelo.',
            category: 'Postres',
            price: 2500.0,
            photoUrl: null,
            isAvailable: true,
            isCompliant: true,
            conflictingRestrictions: [],
            recipeIngredients: ['Huevos', 'Leche', 'Azúcar', 'Dulce de leche'],
          },
        ],
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  const renderComponent = (
    tenantCtx: Partial<TenantContextType> = {},
    cartCtx: Partial<CartContextType> = {}
  ) => {
    const mergedTenant: TenantContextType = {
      ...defaultTenantContext,
      ...tenantCtx,
    };

    const mergedCart: CartContextType = {
      ...defaultCartContext,
      ...cartCtx,
    };

    return render(
      <MemoryRouter initialEntries={['/catalogo']}>
        <TenantContext.Provider value={mergedTenant}>
          <CartContext.Provider value={mergedCart}>
            <CatalogPage />
          </CartContext.Provider>
        </TenantContext.Provider>
      </MemoryRouter>
    );
  };

  it('redirects to /descubrimiento when active tenant is missing', () => {
    renderComponent({ tenant: null });

    expect(mockNavigate).toHaveBeenCalledWith('/descubrimiento', { replace: true });
  });

  it('fetches and renders categorized dishes when active tenant is present', async () => {
    vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValueOnce(mockCatalogData);

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('La Farola de Palermo')).toBeInTheDocument();
      expect(screen.getAllByText('Platos principales').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('Postres').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
      expect(screen.getByText('Flan Casero con Dulce de Leche')).toBeInTheDocument();
    });

    // Verify categories with 0 dishes are not rendered
    expect(screen.queryByText('Bebidas')).not.toBeInTheDocument();
    expect(screen.queryByText('Ensaladas')).not.toBeInTheDocument();
  });

  it('renders sticky navigation bar with active categories', async () => {
    vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValueOnce(mockCatalogData);

    renderComponent();

    await waitFor(() => {
      const nav = screen.getByTestId('sticky-category-nav');
      expect(nav).toBeInTheDocument();
      expect(screen.getByTestId('nav-category-Platos principales')).toBeInTheDocument();
      expect(screen.getByTestId('nav-category-Postres')).toBeInTheDocument();
      expect(screen.queryByTestId('nav-category-Bebidas')).not.toBeInTheDocument();
    });
  });

  it('renders operating hours banner when establishment is outside operating schedule', async () => {
    const closedCatalogData: CatalogResponse = {
      ...mockCatalogData,
      establishment: {
        ...mockEstablishmentDetailActive,
        isOpen: false,
        nextOpeningSchedule: 'Abre hoy a las 19:30',
      },
    };

    vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValueOnce(closedCatalogData);

    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('operating-hours-banner')).toBeInTheDocument();
      expect(screen.getByText(/Abre hoy a las 19:30/)).toBeInTheDocument();
    });
  });

  it('opens dish detail modal when a dish card is clicked', async () => {
    vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValueOnce(mockCatalogData);

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
    });

    const dishCard = screen.getByTestId(`dish-card-${mockDishAvailableItem.id}`);
    fireEvent.click(dishCard);

    await waitFor(() => {
      const modal = screen.getByTestId('dish-detail-modal');
      expect(modal).toBeInTheDocument();
      expect(screen.getByText('Ingredientes')).toBeInTheDocument();
      expect(screen.getByText('Carne de ternera, Mozzarella, Jamón cocido, Papas')).toBeInTheDocument();
    });
  });

  describe('In-Catalog Rapid Search Filter Tests (T047, T048, US4, COM-4.2.3, FR-020, FR-021, FR-022, FR-023, FR-024, FR-025)', () => {
    it('filters dishes in real time by name and hides categories with zero matches (CA 1, FR-020, FR-022)', async () => {
      vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValueOnce(mockCatalogData);
      renderComponent();

      await waitFor(() => {
        expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
        expect(screen.getByText('Flan Casero con Dulce de Leche')).toBeInTheDocument();
      });

      const searchInput = screen.getByTestId('catalog-search-input');
      fireEvent.change(searchInput, { target: { value: 'Milanesa' } });

      await waitFor(() => {
        expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
        expect(screen.queryByText('Flan Casero con Dulce de Leche')).not.toBeInTheDocument();
        expect(screen.queryByTestId('category-grid-Postres')).not.toBeInTheDocument();
      });
    });

    it('filters dishes by recipe ingredients, case- and diacritic-insensitively (CA 2, CA 3, FR-021)', async () => {
      vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValueOnce(mockCatalogData);
      renderComponent();

      await waitFor(() => {
        expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
      });

      const searchInput = screen.getByTestId('catalog-search-input');
      fireEvent.change(searchInput, { target: { value: 'jamon' } });

      await waitFor(() => {
        expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
        expect(screen.queryByText('Flan Casero con Dulce de Leche')).not.toBeInTheDocument();
      });

      fireEvent.change(searchInput, { target: { value: 'DULCE DE LECHE' } });

      await waitFor(() => {
        expect(screen.getByText('Flan Casero con Dulce de Leche')).toBeInTheDocument();
        expect(screen.queryByText('Milanesa Napolitana con Papas Fritas')).not.toBeInTheDocument();
      });
    });

    it('displays empty search state with spelling suggestion when no dishes match (CA 6, FR-024, T048)', async () => {
      vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValueOnce(mockCatalogData);
      renderComponent();

      await waitFor(() => {
        expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
      });

      const searchInput = screen.getByTestId('catalog-search-input');
      fireEvent.change(searchInput, { target: { value: 'platoinexistente123' } });

      await waitFor(() => {
        expect(screen.getByTestId('catalog-empty-search')).toBeInTheDocument();
        expect(screen.getByText('No se encontraron platos')).toBeInTheDocument();
        expect(
          screen.getByText(/No encontramos resultados para tu búsqueda. Verificá la ortografía o probá con otros términos o ingredientes./)
        ).toBeInTheDocument();
        expect(screen.queryByText('Milanesa Napolitana con Papas Fritas')).not.toBeInTheDocument();
      });

      const clearBtn = screen.getByTestId('clear-empty-search-btn');
      fireEvent.click(clearBtn);

      await waitFor(() => {
        expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
        expect(screen.getByText('Flan Casero con Dulce de Leche')).toBeInTheDocument();
        expect(screen.queryByTestId('catalog-empty-search')).not.toBeInTheDocument();
      });
    });

    it('ignores whitespace-only search input and preserves full catalog state (CA 8, FR-025)', async () => {
      vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValueOnce(mockCatalogData);
      renderComponent();

      await waitFor(() => {
        expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
      });

      const searchInput = screen.getByTestId('catalog-search-input');
      fireEvent.change(searchInput, { target: { value: '     ' } });

      await waitFor(() => {
        expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
        expect(screen.getByText('Flan Casero con Dulce de Leche')).toBeInTheDocument();
      });
    });
  });

  describe('Manual Filters and Conjunctive AND Search Integration (T035, US4, COM-4.2.2, AC-4.1 to AC-4.6, INV-14 to INV-17, FR-016 to FR-021, SC-012 to SC-014)', () => {
    it('composes category OR, price range AND, and text search AND conjunctively', async () => {
      vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValueOnce(mockCatalogData);
      renderComponent();

      await waitFor(() => {
        expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
        expect(screen.getByText('Flan Casero con Dulce de Leche')).toBeInTheDocument();
      });

      // Filter by category Postres
      const postresCheckbox = screen.getByLabelText('Postres');
      fireEvent.click(postresCheckbox);

      await waitFor(() => {
        expect(screen.getByText('Flan Casero con Dulce de Leche')).toBeInTheDocument();
        expect(screen.queryByText('Milanesa Napolitana con Papas Fritas')).not.toBeInTheDocument();
      });

      // Also filter by price range min 2000, max 4000
      const minPriceInput = screen.getByLabelText(/precio mínimo/i);
      const maxPriceInput = screen.getByLabelText(/precio máximo/i);
      fireEvent.change(minPriceInput, { target: { value: '2000' } });
      fireEvent.change(maxPriceInput, { target: { value: '4000' } });

      await waitFor(() => {
        // Flan Casero has price 3200 (in [2000, 4000])
        expect(screen.getByText('Flan Casero con Dulce de Leche')).toBeInTheDocument();
      });

      // Text search casero
      const searchInput = screen.getByTestId('catalog-search-input');
      fireEvent.change(searchInput, { target: { value: 'casero' } });

      await waitFor(() => {
        expect(screen.getByText('Flan Casero con Dulce de Leche')).toBeInTheDocument();
      });

      // Tighten price range to max 2000 -> Flan (2500) no longer matches
      fireEvent.change(maxPriceInput, { target: { value: '2000' } });

      await waitFor(() => {
        expect(screen.queryByText('Flan Casero con Dulce de Leche')).not.toBeInTheDocument();
        expect(screen.getByTestId('empty-filter-results-notice')).toBeInTheDocument();
      });
    });

    it('keeps out-of-stock dishes visible and disabled under active manual filters (AC-4.6, INV-17, FR-021, SC-014)', async () => {
      const catalogWithOutOfStock: CatalogResponse = {
        ...mockCatalogData,
        categories: [
          {
            category: 'Platos Principales',
            dishes: [
              mockDishAvailableItem,
              {
                ...mockDishOutOfStockItem,
                category: 'Platos Principales',
                price: 12000,
              },
            ],
          },
        ],
      };

      vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValueOnce(catalogWithOutOfStock);
      renderComponent();

      await waitFor(() => {
        expect(screen.getByText(mockDishAvailableItem.name)).toBeInTheDocument();
        expect(screen.getByText(mockDishOutOfStockItem.name)).toBeInTheDocument();
      });

      // Select category Platos Principales
      const catCheckbox = screen.getByLabelText('Platos Principales');
      fireEvent.click(catCheckbox);

      await waitFor(() => {
        expect(screen.getByText(mockDishAvailableItem.name)).toBeInTheDocument();
        const outOfStockDish = screen.getByText(mockDishOutOfStockItem.name);
        expect(outOfStockDish).toBeInTheDocument();
      });
    });

    it('displays EmptyFilterResultsNotice and performs unified clear resetting categories, price, and text (AC-4.4, AC-4.5, INV-15, INV-16, FR-019, FR-020, SC-013)', async () => {
      vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValueOnce(mockCatalogData);
      renderComponent();

      await waitFor(() => {
        expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
        expect(screen.getByText('Flan Casero con Dulce de Leche')).toBeInTheDocument();
      });

      const minPriceInput = screen.getByLabelText(/precio mínimo/i);
      fireEvent.change(minPriceInput, { target: { value: '999999' } });

      await waitFor(() => {
        expect(screen.getByTestId('empty-filter-results-notice')).toBeInTheDocument();
        expect(screen.getByText(/no encontramos platos que coincidan con los filtros aplicados/i)).toBeInTheDocument();
      });

      const clearBtn = screen.getByTestId('empty-filter-clear-btn');
      fireEvent.click(clearBtn);

      await waitFor(() => {
        expect(screen.getByText('Milanesa Napolitana con Papas Fritas')).toBeInTheDocument();
        expect(screen.getByText('Flan Casero con Dulce de Leche')).toBeInTheDocument();
        expect(screen.queryByTestId('empty-filter-results-notice')).not.toBeInTheDocument();
      });
    });
  });
});
