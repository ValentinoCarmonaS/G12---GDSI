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
      expect(screen.getByText('Platos principales')).toBeInTheDocument();
      expect(screen.getByText('Postres')).toBeInTheDocument();
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
});
