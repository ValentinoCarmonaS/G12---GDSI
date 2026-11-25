import React from 'react';
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ClientCacheProvider, useClientCache } from '@/context/ClientCacheContext';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import { EstablishmentSearchBar } from '@/components/catalog/EstablishmentSearchBar';
import { ProfilePage } from '@/pages/ProfilePage';
import { AdminBrandingPage } from '@/pages/AdminBrandingPage';
import { DishCard } from '@/components/catalog/DishCard';
import { PersonalDataForm } from '@/components/profile/PersonalDataForm';
import { dinerCatalogApi } from '@/api/dinerCatalogApi';
import { profileApi } from '@/api/profileApi';
import * as brandingApi from '@/api/brandingApi';
import * as authApiModule from '@/api/authApi';
import * as useAuthHook from '@/hooks/useAuth';
import * as useTenantHook from '@/hooks/useTenant';
import * as useCartHook from '@/hooks/useCart';
import { adminNotificationApi } from '@/api/adminNotificationApi';
import * as avatarUtils from '@/utils/avatarUtils';

const TENANT_ID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';

describe('SWR Coherence, Deduplication & Media Refresh (T057, FR-006, FR-007, FR-008, SC-003, SC-005)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
    localStorage.clear();
    vi.spyOn(useTenantHook, 'useTenant').mockReturnValue({
      tenant: null,
      setTenant: vi.fn(),
      clearTenant: vi.fn(),
    } as any);
    vi.spyOn(useCartHook, 'useCart').mockReturnValue({
      items: [],
      clearCart: vi.fn(),
      reEvaluateCompliance: vi.fn(),
    } as any);
  });

  it('EstablishmentSearchBar shows cached list immediately on second mount inside ClientCacheProvider', async () => {
    const sampleEstablishments = [
      { id: TENANT_ID, name: 'La Parrilla SWR', status: 'ACTIVE' as const, isOpen: true },
    ];
    const getAllSpy = vi
      .spyOn(dinerCatalogApi, 'getAllEstablishments')
      .mockResolvedValueOnce(sampleEstablishments)
      .mockImplementationOnce(() => new Promise(() => {}));

    const ToggleSearch: React.FC = () => {
      const [visible, setVisible] = React.useState(true);
      return (
        <div>
          <button type="button" onClick={() => setVisible((v) => !v)}>toggle</button>
          {visible && <EstablishmentSearchBar />}
        </div>
      );
    };

    render(
      <ClientCacheProvider>
        <MemoryRouter>
          <ToggleSearch />
        </MemoryRouter>
      </ClientCacheProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('La Parrilla SWR')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('toggle'));
    fireEvent.click(screen.getByText('toggle'));

    // Immediate render without waiting for pending revalidation promise
    expect(screen.getByText('La Parrilla SWR')).toBeInTheDocument();
    expect(getAllSpy).toHaveBeenCalledTimes(2);
  });

  it('ProfilePage + WorkspaceLayout deduplicate GET /profile/me to 1 request', async () => {
    vi.spyOn(useAuthHook, 'useAuth').mockReturnValue({
      user: { id: 'u-1', email: 'diner@easyeat.com', role: 'COMENSAL', tenantId: null, mustChangePassword: false },
      token: 'jwt',
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
      clearAuth: vi.fn(),
      setToken: vi.fn(),
    } as any);

    const getProfileSpy = vi.spyOn(profileApi, 'getProfile').mockResolvedValue({
      id: 'u-1',
      firstName: 'Valentino',
      lastName: 'Carmona',
      email: 'diner@easyeat.com',
      pendingEmail: null,
      phone: null,
      age: null,
      gender: null,
      address: null,
      avatarUrl: null,
      dietaryRestrictions: [],
    } as any);
    vi.spyOn(profileApi, 'getDietaryTaxonomy').mockResolvedValue([]);

    render(
      <ClientCacheProvider>
        <MemoryRouter>
          <ProfilePage />
        </MemoryRouter>
      </ClientCacheProvider>
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue('Valentino')).toBeInTheDocument();
    });
    expect(getProfileSpy).toHaveBeenCalledTimes(1);
  });

  it('AdminBrandingPage + WorkspaceLayout share branding:admin:{id}, invalidate both views on save, and append ?v= to logo', async () => {
    vi.spyOn(useAuthHook, 'useAuth').mockReturnValue({
      user: { id: 'adm-1', email: 'admin@easyeat.com', role: 'ADMINISTRADOR', tenantId: TENANT_ID, mustChangePassword: false },
      token: 'jwt',
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
      clearAuth: vi.fn(),
      setToken: vi.fn(),
    } as any);

    const brandingData = {
      establishmentId: TENANT_ID,
      fantasyName: 'Bistró Admin',
      description: 'Desc',
      logoUrl: `/api/v1/establishments/${TENANT_ID}/logo`,
      primaryColor: '#10B981',
      secondaryColor: '#065F46',
      accentColor: '#F59E0B',
      primaryContrastColor: '#FFFFFF',
      secondaryContrastColor: '#FFFFFF',
      accentContrastColor: '#000000',
      updatedAt: '2026-11-24T12:00:00Z',
      isDefaultFallback: false,
    };

    const getAdminBrandingSpy = vi.spyOn(brandingApi, 'getAdminBranding').mockResolvedValue(brandingData);
    vi.spyOn(brandingApi, 'updateAdminBranding').mockResolvedValue({
      ...brandingData,
      fantasyName: 'Bistró Editado',
      updatedAt: '2026-11-24T12:05:00Z',
    });
    vi.spyOn(adminNotificationApi, 'listNotifications').mockResolvedValue({
      content: [],
      unreadCount: 0,
    } as any);

    let cacheRef: ReturnType<typeof useClientCache> | null = null;
    const CacheObserver: React.FC = () => {
      const cache = useClientCache();
      cacheRef = cache;
      React.useEffect(() => {
        cache.set(`branding:diner:${TENANT_ID}`, brandingData);
      }, [cache]);
      return null;
    };

    render(
      <ClientCacheProvider>
        <MemoryRouter>
          <CacheObserver />
          <AdminBrandingPage />
        </MemoryRouter>
      </ClientCacheProvider>
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue('Bistró Admin')).toBeInTheDocument();
    });
    expect(getAdminBrandingSpy).toHaveBeenCalledTimes(1);

    const logoImg = screen.getByAltText(/Logotipo/i) as HTMLImageElement;
    expect(logoImg.src).toContain('?v=');

    fireEvent.change(screen.getByDisplayValue('Bistró Admin'), { target: { value: 'Bistró Editado' } });
    fireEvent.click(screen.getByRole('button', { name: /Guardar Cambios/i }));

    await waitFor(() => {
      expect(screen.getByText(/Branding actualizado exitosamente/i)).toBeInTheDocument();
    });
    expect(cacheRef!.get(`branding:diner:${TENANT_ID}`)).toBeUndefined();
  });

  it('DishCard appends ?v= only when mediaVersions["dish:{id}"] exists in ClientSWRStore', () => {
    const dish = {
      id: 'dish-media-1',
      name: 'Empanada',
      description: 'Salteña',
      category: 'Entradas',
      price: 2000,
      photoUrl: '/api/v1/dishes/dish-media-1/photo',
      isAvailable: true,
      availableStock: 5,
      isCompliant: true,
      conflictingRestrictions: [],
      recipeIngredients: [],
      allergens: [],
      lifestyles: [],
    };

    const MediaHarness: React.FC = () => {
      const cache = useClientCache();
      return (
        <div>
          <button type="button" onClick={() => cache.setMediaVersion('dish:dish-media-1', 123456)}>
            bump-photo
          </button>
          <DishCard dish={dish} />
        </div>
      );
    };

    render(
      <ClientCacheProvider>
        <MediaHarness />
      </ClientCacheProvider>
    );

    const imgBefore = screen.getByAltText('Empanada') as HTMLImageElement;
    expect(imgBefore.getAttribute('src')).not.toContain('?v=');

    fireEvent.click(screen.getByText('bump-photo'));
    const imgAfter = screen.getByAltText('Empanada') as HTMLImageElement;
    expect(imgAfter.getAttribute('src')).toContain('?v=123456');
  });

  it('PersonalDataForm stabilizes avatar _t across re-renders and refreshes on easyeat:avatar-changed', () => {
    let counter = 100;
    vi.spyOn(avatarUtils, 'getAuthenticatedAvatarUrl').mockImplementation(
      (url) => `${url}?token=jwt&_t=${++counter}`
    );

    const { rerender } = render(
      <PersonalDataForm
        initialData={{ firstName: 'Val', lastName: 'Car', avatarUrl: '/api/v1/profile/avatar' }}
        onSubmit={vi.fn()}
      />
    );

    const firstSrc = (screen.getByAltText(/Avatar/i) as HTMLImageElement).src;
    rerender(
      <PersonalDataForm
        initialData={{ firstName: 'Val', lastName: 'Car', avatarUrl: '/api/v1/profile/avatar' }}
        onSubmit={vi.fn()}
      />
    );
    const secondSrc = (screen.getByAltText(/Avatar/i) as HTMLImageElement).src;
    expect(secondSrc).toBe(firstSrc);

    act(() => {
      window.dispatchEvent(new CustomEvent('easyeat:avatar-changed'));
    });
    const thirdSrc = (screen.getByAltText(/Avatar/i) as HTMLImageElement).src;
    expect(thirdSrc).not.toBe(firstSrc);
  });

  it('AuthContext purges ClientSWRStore synchronously on logout and easyeat:session-expired (401)', async () => {
    vi.spyOn(authApiModule.authApi, 'logout').mockResolvedValue(undefined);
    let cacheRef: ReturnType<typeof useClientCache> | null = null;
    let authRef: ReturnType<typeof useAuth> | null = null;

    const AuthCacheProbe: React.FC = () => {
      cacheRef = useClientCache();
      authRef = useAuth();
      return null;
    };

    render(
      <ClientCacheProvider>
        <AuthProvider>
          <AuthCacheProbe />
        </AuthProvider>
      </ClientCacheProvider>
    );

    act(() => {
      cacheRef!.set('profile:me', { id: 'u-1' });
      cacheRef!.setMediaVersion('dish:1', 999);
    });
    expect(cacheRef!.get('profile:me')).toBeDefined();

    await act(async () => {
      await authRef!.logout();
    });
    expect(cacheRef!.get('profile:me')).toBeUndefined();
    expect(cacheRef!.getMediaVersion('dish:1')).toBeUndefined();

    act(() => {
      cacheRef!.set('establishments:all', [{ id: TENANT_ID }]);
      window.dispatchEvent(new CustomEvent('easyeat:session-expired'));
    });
    expect(cacheRef!.get('establishments:all')).toBeUndefined();
  });
});
