import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ThemeScopeProvider } from '../../src/theme/ThemeScopeProvider';
import { CatalogPage } from '../../src/pages/CatalogPage';
import { DishCard } from '../../src/components/catalog/DishCard';
import { DishDetailModal } from '../../src/components/catalog/DishDetailModal';
import { EstablishmentSwitchModal } from '../../src/components/catalog/EstablishmentSwitchModal';
import { AddToCartRiskModal } from '../../src/components/cart/AddToCartRiskModal';
import { CartDrawer } from '../../src/components/cart/CartDrawer';
import { ManualFiltersSidebar } from '../../src/components/catalog/ManualFiltersSidebar';
import { CatalogMasterToggle } from '../../src/components/catalog/CatalogMasterToggle';
import { TenantContext } from '../../src/context/TenantContext';
import { CartContext } from '../../src/context/CartContext';
import { CatalogFilterProvider } from '../../src/context/CatalogFilterContext';
import { dinerCatalogApi } from '../../src/api/dinerCatalogApi';
import { getDinerBranding } from '../../src/api/brandingApi';
import type { DishCatalogItem } from '../../src/types/dinerCatalog';

vi.mock('../../src/api/dinerCatalogApi', () => ({
  dinerCatalogApi: {
    getEstablishmentCatalog: vi.fn(),
    getRealtimeAvailability: vi.fn(),
  },
}));

vi.mock('../../src/api/brandingApi', () => ({
  getDinerBranding: vi.fn(),
  brandingApi: {
    getPublicBranding: vi.fn(),
  },
}));

vi.mock('../../src/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({
    isAuthenticated: false,
    user: null,
    logout: vi.fn(),
  })),
}));

vi.mock('../../src/hooks/useProfile', () => ({
  useProfile: vi.fn(() => ({
    profile: null,
    isLoading: false,
  })),
}));

vi.mock('../../src/hooks/useCatalog', () => ({
  useCatalog: vi.fn(() => ({
    dishes: [],
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  })),
}));

vi.mock('../../src/hooks/useRealtimeAvailability', () => ({
  useRealtimeAvailability: vi.fn(() => ({
    availability: {},
    isConnectionLost: false,
  })),
}));

const sampleDish: DishCatalogItem = {
  id: 'dish-1',
  name: 'Milanesa Napolitana',
  description: 'Con papas fritas caseras',
  price: 12500,
  imageUrl: null,
  isAvailable: true,
  isCompliant: true,
  allergens: ['Gluten', 'Lácteos'],
  lifestyles: ['Sin azúcar'],
  ingredients: [{ name: 'Carne vacuna', isAllergen: false }],
  availableStock: 5,
};

const sampleCatalogResponse = {
  establishment: {
    id: 'est-1',
    name: 'Bistró Bento',
    isOpen: true,
    nextOpeningSchedule: null,
  },
  categories: [
    {
      category: 'Principales',
      dishes: [sampleDish],
    },
    {
      category: 'Postres',
      dishes: [
        {
          ...sampleDish,
          id: 'dish-2',
          name: 'Flan Casero',
          price: 4500,
          allergens: ['Huevo'],
          lifestyles: ['Vegetariano'],
        },
      ],
    },
  ],
};

const mockTenantContext = {
  tenant: { id: 'est-1', name: 'Bistró Bento' },
  isLoading: false,
  selectTenant: vi.fn(),
  clearTenant: vi.fn(),
};

const mockCartContext: any = {
  items: [
    {
      dish: {
        id: 'dish-1',
        tenantId: 'est-1',
        name: 'Milanesa Napolitana',
        price: 12500,
        description: 'Con papas fritas caseras',
        isActive: true,
        isCompliant: true,
        availableStock: 5,
      },
      quantity: 2,
      acceptedRisk: false,
    },
  ],
  totalItems: 2,
  subtotal: 25000,
  addItem: vi.fn(),
  removeItem: vi.fn(),
  updateQuantity: vi.fn(),
  clearCart: vi.fn(),
  isDishInCart: vi.fn((id: string) => id === 'dish-1'),
  getDishQuantity: vi.fn((id: string) => (id === 'dish-1' ? 2 : 0)),
  hasRiskItems: false,
  hasPausedOrOutOfStockItems: false,
  refreshCartAvailability: vi.fn(),
};

function renderWithProviders(ui: React.ReactElement) {
  return render(
    <MemoryRouter initialEntries={['/catalogo']}>
      <ThemeScopeProvider>
        <TenantContext.Provider value={mockTenantContext as any}>
          <CartContext.Provider value={mockCartContext}>
            <CatalogFilterProvider>{ui}</CatalogFilterProvider>
          </CartContext.Provider>
        </TenantContext.Provider>
      </ThemeScopeProvider>
    </MemoryRouter>
  );
}

describe('Spec 025 US2 — Catálogo Bento, seguridad alimentaria, carrito y brand slots', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (dinerCatalogApi.getEstablishmentCatalog as any).mockResolvedValue(sampleCatalogResponse);
    (getDinerBranding as any).mockResolvedValue({
      fantasyName: 'Bistró Bento',
      description: 'Cocina de autor',
      logoUrl: null,
      primaryColor: '#000000',
      primaryContrastColor: '#FFFFFF',
      secondaryColor: '#FFEE00',
      secondaryContrastColor: '#111827',
      accentColor: '#9A4A24',
      accentContrastColor: '#FFFFFF',
      isDefaultFallback: false,
    });
  });

  it('aplica data-brand-slot exclusivamente en header, category y cta y evita emojis y clases Tailwind v4', async () => {
    const { container } = renderWithProviders(<CatalogPage />);

    await waitFor(() => {
      expect(screen.getByTestId('restaurant-branding-header')).toBeInTheDocument();
    });

    const headerSlot = container.querySelector('[data-brand-slot="header"]');
    const categorySlot = container.querySelector('[data-brand-slot="category"]');
    const ctaSlots = container.querySelectorAll('[data-brand-slot="cta"]');

    expect(headerSlot).toBeInTheDocument();
    expect(categorySlot).toBeInTheDocument();
    expect(ctaSlots.length).toBeGreaterThan(0);

    // No literal emojis in alerts/banners
    expect(container.textContent).not.toMatch(/[ℹ️⚠️⏸️]/u);

    // No Tailwind v4 orphan classes
    const html = container.innerHTML;
    expect(html).not.toContain('shadow-2xs');
    expect(html).not.toContain('bg-linear-to-br');
    expect(html).not.toContain('backdrop-blur-xs');
  });

  it('provee sidebar colapsable en escritorio y botón Filtros (N) con AccessibleDrawer en móvil', async () => {
    renderWithProviders(<CatalogPage />);

    await waitFor(() => {
      expect(screen.getByTestId('mobile-filters-trigger')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('mobile-filters-trigger'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  it('DishCard es operable por teclado, muestra precio en font-ee-mono tabular-nums, badge quantityInCart y píldoras neutras para invitados', () => {
    const onSelect = vi.fn();
    const onAdd = vi.fn();

    const { container } = renderWithProviders(
      <DishCard
        dish={sampleDish}
        onSelectDish={onSelect}
        onAddToCart={onAdd}
        showWarnings={false}
      />
    );

    // Price in JetBrains Mono tabular-nums
    const priceEl = screen.getByTestId(`dish-price-${sampleDish.id}`);
    expect(priceEl.className).toContain('font-ee-mono');
    expect(priceEl.className).toContain('tabular-nums');

    // Quantity in cart badge visible
    expect(screen.getByTestId(`dish-cart-qty-${sampleDish.id}`)).toHaveTextContent('2');

    // Neutral allergen pills visible for guests when showWarnings is false
    expect(screen.getByText('Gluten')).toBeInTheDocument();

    // Detail action is a focusable button, not an inaccessible clickable div
    const detailBtn = screen.getByTestId(`dish-detail-trigger-${sampleDish.id}`);
    expect(detailBtn.tagName).toBe('BUTTON');
    fireEvent.click(detailBtn);
    expect(onSelect).toHaveBeenCalledWith(sampleDish);

    // CTA has data-brand-slot="cta" and min-h-[44px]
    const ctaBtn = container.querySelector('[data-brand-slot="cta"]');
    expect(ctaBtn).toBeInTheDocument();
    expect(ctaBtn?.className).toContain('min-h-[44px]');
  });

  it('overlays (DishDetailModal, AddToCartRiskModal, CartDrawer, EstablishmentSwitchModal) exponen role="dialog" y cierran con Escape', () => {
    const onCloseDetail = vi.fn();
    const { unmount: unmountDetail } = renderWithProviders(
      <DishDetailModal
        dish={sampleDish}
        isOpen={true}
        onClose={onCloseDetail}
        onAddToCart={vi.fn()}
      />
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onCloseDetail).toHaveBeenCalledTimes(1);
    unmountDetail();

    const onCancelRisk = vi.fn();
    const { unmount: unmountRisk } = renderWithProviders(
      <AddToCartRiskModal
        isOpen={true}
        dishName="Milanesa"
        conflictingRestrictions={['Gluten']}
        onConfirm={vi.fn()}
        onCancel={onCancelRisk}
      />
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onCancelRisk).toHaveBeenCalledTimes(1);
    unmountRisk();

    const onCloseCart = vi.fn();
    const { unmount: unmountCart } = renderWithProviders(
      <CartDrawer isOpen={true} onClose={onCloseCart} onCheckout={vi.fn()} />
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onCloseCart).toHaveBeenCalledTimes(1);
    unmountCart();

    const onCancelSwitch = vi.fn();
    renderWithProviders(
      <EstablishmentSwitchModal
        isOpen={true}
        currentEstablishmentName="Local A"
        newEstablishmentName="Local B"
        cartItemsCount={2}
        onConfirm={vi.fn()}
        onCancel={onCancelSwitch}
      />
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onCancelSwitch).toHaveBeenCalledTimes(1);
  });

  it('ManualFiltersSidebar muestra contador de filtros activos y chips removibles sin clases Tailwind v4', () => {
    const onCategoryToggle = vi.fn();
    const { container } = renderWithProviders(
      <ManualFiltersSidebar
        categories={['Principales', 'Postres']}
        selectedCategories={['Principales']}
        onCategoryToggle={onCategoryToggle}
        lifestyles={['Vegetariano']}
        selectedLifestyles={[]}
        onLifestyleToggle={vi.fn()}
        allergens={['Gluten']}
        selectedAllergens={['Gluten']}
        onAllergenToggle={vi.fn()}
        minPrice=""
        maxPrice=""
        onMinPriceChange={vi.fn()}
        onMaxPriceChange={vi.fn()}
        onClearFilters={vi.fn()}
      />
    );

    expect(screen.getByTestId('active-filters-count')).toHaveTextContent('2');
    expect(container.innerHTML).not.toContain('shadow-2xs');
  });

  it('CatalogMasterToggle no renderiza tarjetas anidadas ni emojis', () => {
    const { container } = renderWithProviders(
      <CatalogMasterToggle
        activeRestrictionsCount={2}
        isFilterActive={true}
        onToggle={vi.fn()}
      />
    );
    expect(container.textContent).not.toMatch(/[ℹ️⚠️⏸️]/u);
    expect(container.innerHTML).not.toContain('shadow-xs');
  });
});
