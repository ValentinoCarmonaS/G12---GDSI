import { render, screen, fireEvent, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { CatalogPage } from '../../src/pages/CatalogPage';
import * as useAuthHook from '../../src/hooks/useAuth';
import * as useCatalogHook from '../../src/hooks/useCatalog';
import * as useProfileHook from '../../src/hooks/useProfile';
import { EnrichedDish } from '../../src/types/catalog';

describe('CatalogPage Component', () => {
  const mockRefetch = vi.fn();

  const compliantDish: EnrichedDish = {
    id: 'dish-1',
    tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    name: 'Arroz con Verduras',
    description: 'Arroz salteado con vegetales frescos',
    price: 1200,
    isActive: true,
    isCompliant: true,
    conflictingRestrictions: [],
  };

  const conflictingDish: EnrichedDish = {
    id: 'dish-2',
    tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    name: 'Milanesa con Puré',
    description: 'Milanesa con puré de papas',
    price: 2500,
    isActive: true,
    isCompliant: false,
    conflictingRestrictions: ['DAIRY', 'WHEAT'],
  };

  const allDishes: EnrichedDish[] = [compliantDish, conflictingDish];

  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  const renderWithAuth = (
    isAuthenticated = false,
    userRole = 'COMENSAL',
    restrictions: string[] = []
  ) => {
    vi.spyOn(useAuthHook, 'useAuth').mockReturnValue({
      isAuthenticated,
      user: isAuthenticated
        ? {
            id: 'user-123',
            email: 'comensal@easyeat.com',
            role: userRole as any,
            tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
            tokenVersion: 1,
            mustChangePassword: false,
          }
        : null,
      token: isAuthenticated ? 'dummy-token' : null,
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    const profileData = {
      id: 'user-123',
      name: 'Test Comensal',
      email: 'comensal@easyeat.com',
      pendingEmail: null,
      phoneNumber: '1122334455',
      dietaryRestrictions: restrictions.map((code) => ({
        code,
        name: code,
        category: 'ALLERGEN' as any,
        isCustom: false,
      })),
    };

    vi.spyOn(useProfileHook, 'useProfile').mockReturnValue({
      profile: profileData,
      taxonomy: [],
      loading: false,
      taxonomyLoading: false,
      error: null,
      cooldownSeconds: 0,
      setCooldownSeconds: vi.fn(),
      refreshProfile: vi.fn(),
      refreshTaxonomy: vi.fn(),
      updatePersonalData: vi.fn(),
      updateDemographics: vi.fn(),
      uploadAvatar: vi.fn(),
      removeAvatar: vi.fn(),
      updateDietaryRestrictions: vi.fn(),
      requestEmailChange: vi.fn(),
      resendVerification: vi.fn(),
      cancelEmailChange: vi.fn(),
    });

    return render(
      <MemoryRouter>
        <CatalogPage />
      </MemoryRouter>
    );
  };

  it('renders guest banner and all dishes without dietary master toggle for unauthenticated guest (FR-026)', () => {
    vi.spyOn(useCatalogHook, 'useCatalog').mockReturnValue({
      dishes: allDishes,
      isLoading: false,
      error: null,
      tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      refetch: mockRefetch,
    });

    renderWithAuth(false);

    expect(screen.getByTestId('guest-banner')).toBeInTheDocument();
    expect(screen.getByText(/Estás navegando como invitado/i)).toBeInTheDocument();
    expect(screen.getByTestId('guest-login-link')).toBeInTheDocument();
    expect(screen.getByTestId('guest-register-link')).toBeInTheDocument();

    expect(screen.queryByTestId('catalog-master-toggle')).not.toBeInTheDocument();
    expect(screen.getByText('Arroz con Verduras')).toBeInTheDocument();
    expect(screen.getByText('Milanesa con Puré')).toBeInTheDocument();
  });

  it('renders loading state when dishes are being fetched', () => {
    vi.spyOn(useCatalogHook, 'useCatalog').mockReturnValue({
      dishes: [],
      isLoading: true,
      error: null,
      tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      refetch: mockRefetch,
    });

    renderWithAuth(true, 'COMENSAL', ['CELIAC']);

    expect(screen.getByTestId('catalog-loading')).toBeInTheDocument();
    expect(screen.getByText(/Cargando menú del comedor.../i)).toBeInTheDocument();
  });

  it('renders error state and provides retry trigger', () => {
    vi.spyOn(useCatalogHook, 'useCatalog').mockReturnValue({
      dishes: [],
      isLoading: false,
      error: 'Error de conexión con el catálogo',
      tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      refetch: mockRefetch,
    });

    renderWithAuth(true, 'COMENSAL', ['CELIAC']);

    expect(screen.getByTestId('catalog-error')).toBeInTheDocument();
    expect(screen.getByText('Error de conexión con el catálogo')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('catalog-retry-button'));
    expect(mockRefetch).toHaveBeenCalledTimes(1);
  });

  it('filters conflicting dishes by default when diner has active restrictions and master toggle is ON (FR-023)', () => {
    vi.spyOn(useCatalogHook, 'useCatalog').mockReturnValue({
      dishes: allDishes,
      isLoading: false,
      error: null,
      tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      refetch: mockRefetch,
    });

    renderWithAuth(true, 'COMENSAL', ['DAIRY', 'WHEAT']);

    expect(screen.getByTestId('catalog-master-toggle-container')).toBeInTheDocument();
    expect(screen.getByText('Arroz con Verduras')).toBeInTheDocument();
    // Conflicting dish should be filtered out when toggle is ON
    expect(screen.queryByText('Milanesa con Puré')).not.toBeInTheDocument();
  });

  it('shows full menu with warning badges when master toggle is flipped OFF (FR-020, FR-023)', () => {
    vi.spyOn(useCatalogHook, 'useCatalog').mockReturnValue({
      dishes: allDishes,
      isLoading: false,
      error: null,
      tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      refetch: mockRefetch,
    });

    renderWithAuth(true, 'COMENSAL', ['DAIRY', 'WHEAT']);

    const toggleBtn = screen.getByTestId('catalog-master-toggle-button');
    fireEvent.click(toggleBtn);

    // Now both dishes should appear
    expect(screen.getByText('Arroz con Verduras')).toBeInTheDocument();
    expect(screen.getByText('Milanesa con Puré')).toBeInTheDocument();
    expect(screen.getByText(/Contiene Lácteos/i)).toBeInTheDocument();
    expect(screen.getByText(/Contiene Trigo/i)).toBeInTheDocument();
  });

  it('renders EmptyCatalogDietaryState when zero dishes match active restrictions with action to view full menu (FR-024)', () => {
    // Only conflicting dish available
    vi.spyOn(useCatalogHook, 'useCatalog').mockReturnValue({
      dishes: [conflictingDish],
      isLoading: false,
      error: null,
      tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      refetch: mockRefetch,
    });

    renderWithAuth(true, 'COMENSAL', ['DAIRY']);

    expect(screen.getByTestId('empty-catalog-dietary-state')).toBeInTheDocument();
    expect(screen.getByText(/No encontramos platos que coincidan con tu dieta/i)).toBeInTheDocument();

    const viewFullMenuBtn = screen.getByTestId('view-full-menu-button');
    fireEvent.click(viewFullMenuBtn);

    // After clicking view full menu, toggle flips OFF and conflicting dish is shown
    expect(screen.getByText('Milanesa con Puré')).toBeInTheDocument();
  });

  it('intercepts addition of non-compliant dish with AddToCartRiskModal (FR-021) and updates cart on confirm', async () => {
    vi.spyOn(useCatalogHook, 'useCatalog').mockReturnValue({
      dishes: allDishes,
      isLoading: false,
      error: null,
      tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      refetch: mockRefetch,
    });

    renderWithAuth(true, 'COMENSAL', ['DAIRY']);

    // Flip toggle OFF so we see the conflicting dish
    fireEvent.click(screen.getByTestId('catalog-master-toggle-button'));

    // Click "Agregar al carrito" on conflicting dish
    const addConflictingBtn = screen.getByTestId('add-to-cart-btn-dish-2');
    fireEvent.click(addConflictingBtn);

    // Preventive modal intercept
    expect(screen.getByTestId('add-to-cart-risk-modal')).toBeInTheDocument();
    expect(screen.getByText(/Advertencia de Seguridad Alimentaria/i)).toBeInTheDocument();

    // Cancel first
    fireEvent.click(screen.getByTestId('risk-modal-cancel-button'));
    expect(screen.queryByTestId('add-to-cart-risk-modal')).not.toBeInTheDocument();
    expect(screen.queryByTestId('cart-badge-count')).not.toBeInTheDocument();

    // Re-open, acknowledge risk checkbox, and confirm
    fireEvent.click(addConflictingBtn);
    expect(screen.getByTestId('add-to-cart-risk-modal')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('risk-modal-acknowledge-checkbox'));
    fireEvent.click(screen.getByTestId('risk-modal-confirm-button'));

    // Modal closed and cart item added
    expect(screen.queryByTestId('add-to-cart-risk-modal')).not.toBeInTheDocument();
    expect(screen.getByTestId('cart-badge-count')).toHaveTextContent('1');

    // Open CartDrawer to verify item with persistent warning
    fireEvent.click(screen.getByTestId('open-cart-button'));
    const drawer = screen.getByTestId('cart-drawer');
    expect(drawer).toBeInTheDocument();
    expect(screen.getByTestId('cart-conflict-banner')).toBeInTheDocument();
    expect(within(drawer).getByText('Milanesa con Puré')).toBeInTheDocument();
  });
});
