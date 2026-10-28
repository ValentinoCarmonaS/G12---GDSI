import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { EstablishmentDiscoveryPage } from '@/pages/EstablishmentDiscoveryPage';
import { TenantContext, TenantContextType } from '@/context/TenantContext';
import { CartContext, CartContextType } from '@/context/CartContext';
import { mockEstablishmentSummaryActive } from '@/test/mocks/dinerCatalogHandlers';

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    user: { email: 'comensal@easyeat.com', role: 'COMENSAL' },
    isAuthenticated: true,
    logout: vi.fn(),
  }),
}));

describe('EstablishmentDiscoveryPage Tests (T022, US1, COM-1.5.1)', () => {
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

  it('renders page header, hero text, and establishment search input', () => {
    render(
      <MemoryRouter>
        <TenantContext.Provider value={defaultTenantContext}>
          <CartContext.Provider value={defaultCartContext}>
            <EstablishmentDiscoveryPage />
          </CartContext.Provider>
        </TenantContext.Provider>
      </MemoryRouter>
    );

    expect(screen.getByText('Descubrimiento de Restaurantes')).toBeInTheDocument();
    expect(screen.getByText('¿Dónde querés pedir hoy?')).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText(/buscar restaurante por nombre/i)
    ).toBeInTheDocument();
  });

  it('renders active establishment banner when tenant is selected in context', () => {
    render(
      <MemoryRouter>
        <TenantContext.Provider
          value={{
            ...defaultTenantContext,
            tenant: mockEstablishmentSummaryActive,
          }}
        >
          <CartContext.Provider value={defaultCartContext}>
            <EstablishmentDiscoveryPage />
          </CartContext.Provider>
        </TenantContext.Provider>
      </MemoryRouter>
    );

    expect(screen.getByText('Restaurante Activo')).toBeInTheDocument();
    expect(screen.getByText(mockEstablishmentSummaryActive.name)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /ver catálogo/i })).toHaveAttribute(
      'href',
      '/catalogo'
    );
  });
});
