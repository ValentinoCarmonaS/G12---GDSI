import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { EstablishmentSearchBar } from '@/components/catalog/EstablishmentSearchBar';
import { dinerCatalogApi } from '@/api/dinerCatalogApi';
import { CartContext, CartContextType } from '@/context/CartContext';
import { TenantContext, TenantContextType } from '@/context/TenantContext';
import {
  mockEstablishmentSummaryActive,
  mockEstablishmentSummaryActive2,
} from '@/test/mocks/dinerCatalogHandlers';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('EstablishmentSearchBar Component Tests (T014, US1, COM-1.5.1, CA 1, CA 2, CA 3, CA 5, CA 6)', () => {
  let mockSetTenant: ReturnType<typeof vi.fn>;
  let mockClearCart: ReturnType<typeof vi.fn>;

  const defaultTenantContext: TenantContextType = {
    tenant: null,
    setTenant: vi.fn(),
    clearTenant: vi.fn(),
    isLoading: false,
  };

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

  const renderComponent = (
    tenantCtx: Partial<TenantContextType> = {},
    cartCtx: Partial<CartContextType> = {}
  ) => {
    mockSetTenant = vi.fn();
    mockClearCart = vi.fn();

    const mergedTenant: TenantContextType = {
      ...defaultTenantContext,
      setTenant: mockSetTenant,
      ...tenantCtx,
    };

    const mergedCart: CartContextType = {
      ...defaultCartContext,
      clearCart: mockClearCart,
      ...cartCtx,
    };

    return render(
      <MemoryRouter>
        <TenantContext.Provider value={mergedTenant}>
          <CartContext.Provider value={mergedCart}>
            <EstablishmentSearchBar />
          </CartContext.Provider>
        </TenantContext.Provider>
      </MemoryRouter>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders search input element with correct placeholder and accessible role', () => {
    renderComponent();
    const input = screen.getByPlaceholderText(/buscar restaurante por nombre/i);
    expect(input).toBeInTheDocument();
  });

  it('does not trigger search when typing fewer than 3 characters (COM-1.5.1 CA 1, CA 6)', async () => {
    const searchSpy = vi.spyOn(dinerCatalogApi, 'searchEstablishments');
    renderComponent();

    const input = screen.getByPlaceholderText(/buscar restaurante por nombre/i);
    await userEvent.type(input, 'ca');

    await new Promise((r) => setTimeout(r, 350));
    expect(searchSpy).not.toHaveBeenCalled();
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('debounces input by 300ms and displays matching establishments (COM-1.5.1 CA 1, CA 2)', async () => {
    const searchSpy = vi.spyOn(dinerCatalogApi, 'searchEstablishments').mockResolvedValue([
      mockEstablishmentSummaryActive,
      mockEstablishmentSummaryActive2,
    ]);

    renderComponent();
    const input = screen.getByPlaceholderText(/buscar restaurante por nombre/i);

    await userEvent.type(input, 'palermo');

    await waitFor(() => {
      expect(searchSpy).toHaveBeenCalledWith('palermo');
    });

    expect(await screen.findByText('La Farola de Palermo')).toBeInTheDocument();
    expect(screen.getByText('Parrilla Don Julio')).toBeInTheDocument();
  });

  it('displays empty state message when no active establishments match (COM-1.5.1 CA 3)', async () => {
    vi.spyOn(dinerCatalogApi, 'searchEstablishments').mockResolvedValue([]);

    renderComponent();
    const input = screen.getByPlaceholderText(/buscar restaurante por nombre/i);

    await userEvent.type(input, 'inexistente');

    expect(
      await screen.findByText(/no se encontraron establecimientos/i)
    ).toBeInTheDocument();
  });

  it('selects establishment directly when cart is empty and navigates to /catalogo (COM-1.5.1 CA 2)', async () => {
    vi.spyOn(dinerCatalogApi, 'searchEstablishments').mockResolvedValue([
      mockEstablishmentSummaryActive,
    ]);

    renderComponent();
    const input = screen.getByPlaceholderText(/buscar restaurante por nombre/i);
    await userEvent.type(input, 'farola');

    const resultItem = await screen.findByText('La Farola de Palermo');
    fireEvent.click(resultItem);

    expect(mockSetTenant).toHaveBeenCalledWith(mockEstablishmentSummaryActive);
    expect(mockNavigate).toHaveBeenCalledWith('/catalogo');
  });

  it('intercepts establishment switch with modal when cart has items (COM-1.5.1 CA 5)', async () => {
    vi.spyOn(dinerCatalogApi, 'searchEstablishments').mockResolvedValue([
      mockEstablishmentSummaryActive2,
    ]);

    // Cart with items and different active tenant
    renderComponent(
      { tenant: mockEstablishmentSummaryActive },
      {
        items: [
          {
            dishId: 'dish-1',
            name: 'Milanesa',
            price: 5000,
            quantity: 2,
            isCompliant: true,
          },
        ],
      }
    );

    const input = screen.getByPlaceholderText(/buscar restaurante por nombre/i);
    await userEvent.type(input, 'don julio');

    const resultItem = await screen.findByText('Parrilla Don Julio');
    fireEvent.click(resultItem);

    // Modal should appear warning about cart clearing
    expect(
      await screen.findByText(/¿cambiar de restaurante\?/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/se vaciará tu carrito/i)).toBeInTheDocument();
    expect(mockSetTenant).not.toHaveBeenCalled();
    expect(mockClearCart).not.toHaveBeenCalled();

    // Confirm switch
    const confirmButton = screen.getByRole('button', { name: /cambiar de local/i });
    fireEvent.click(confirmButton);

    expect(mockClearCart).toHaveBeenCalled();
    expect(mockSetTenant).toHaveBeenCalledWith(mockEstablishmentSummaryActive2);
    expect(mockNavigate).toHaveBeenCalledWith('/catalogo');
  });

  it('cancels switch modal and keeps existing establishment and cart (COM-1.5.1 CA 5)', async () => {
    vi.spyOn(dinerCatalogApi, 'searchEstablishments').mockResolvedValue([
      mockEstablishmentSummaryActive2,
    ]);

    renderComponent(
      { tenant: mockEstablishmentSummaryActive },
      {
        items: [
          {
            dishId: 'dish-1',
            name: 'Milanesa',
            price: 5000,
            quantity: 1,
            isCompliant: true,
          },
        ],
      }
    );

    const input = screen.getByPlaceholderText(/buscar restaurante por nombre/i);
    await userEvent.type(input, 'don julio');

    const resultItem = await screen.findByText('Parrilla Don Julio');
    fireEvent.click(resultItem);

    expect(await screen.findByText(/¿cambiar de restaurante\?/i)).toBeInTheDocument();

    const cancelButton = screen.getByRole('button', { name: /cancelar/i });
    fireEvent.click(cancelButton);

    expect(mockClearCart).not.toHaveBeenCalled();
    expect(mockSetTenant).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(screen.queryByText(/¿cambiar de restaurante\?/i)).not.toBeInTheDocument();
  });
});
