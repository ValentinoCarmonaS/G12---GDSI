import React from 'react';
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { CatalogPage } from '@/pages/CatalogPage';
import { ClientCacheProvider } from '@/context/ClientCacheContext';
import { TENANT_STORAGE_KEY } from '@/context/TenantContext';
import * as useAuthHook from '@/hooks/useAuth';
import { profileApi } from '@/api/profileApi';
import { dinerCatalogApi } from '@/api/dinerCatalogApi';
import { catalogApi } from '@/api/catalogApi';
import * as brandingApi from '@/api/brandingApi';

const TENANT_ID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';

const sampleCatalogResponse = {
  establishment: {
    id: TENANT_ID,
    name: 'Bistró Central',
    status: 'ACTIVE' as const,
    isOpen: true,
    nextOpeningSchedule: null,
  },
  categories: [
    {
      category: 'Entradas',
      dishes: [
        {
          id: 'dish-1',
          name: 'Bruschetta Clásica',
          description: 'Pan tostado con tomate y albahaca',
          category: 'Entradas',
          price: 3500,
          photoUrl: null,
          isAvailable: true,
          availableStock: 10,
          isCompliant: true,
          conflictingRestrictions: [],
          recipeIngredients: ['Pan', 'Tomate'],
          allergens: [],
          lifestyles: ['Vegetariano'],
        },
      ],
    },
    {
      category: 'Platos principales',
      dishes: [
        {
          id: 'dish-2',
          name: 'Milanesa Napolitana',
          description: 'Con papas rústicas',
          category: 'Platos principales',
          price: 9800,
          photoUrl: null,
          isAvailable: true,
          availableStock: 6,
          isCompliant: true,
          conflictingRestrictions: [],
          recipeIngredients: ['Carne', 'Tomate'],
          allergens: [],
          lifestyles: [],
        },
      ],
    },
  ],
};

const sampleProfile = {
  id: 'user-comensal-1',
  name: 'Valentino',
  email: 'valen@easyeat.com',
  pendingEmail: null,
  phoneNumber: '1122334455',
  avatarUrl: null,
  dietaryRestrictions: [],
};

const sampleBranding = {
  establishmentId: TENANT_ID,
  fantasyName: 'Bistró Central',
  description: 'Cocina de autor',
  logoUrl: '/api/v1/establishments/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/logo',
  primaryColor: '#FAD4C0',
  secondaryColor: '#80A1C1',
  accentColor: '#9A4A24',
  primaryContrastColor: '#111827',
  secondaryContrastColor: '#111827',
  accentContrastColor: '#FFFFFF',
  updatedAt: '2026-11-24T12:00:00Z',
  isDefaultFallback: false,
};

describe('CatalogPage Performance, Deduplication & SWR (T020, FR-007, FR-008, SC-003, SC-004)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    localStorage.clear();
    window.scrollTo = vi.fn();
    sessionStorage.setItem(
      TENANT_STORAGE_KEY,
      JSON.stringify({
        id: TENANT_ID,
        name: 'Bistró Central',
        status: 'ACTIVE',
        isOpen: true,
        nextOpeningSchedule: null,
      })
    );

    vi.spyOn(useAuthHook, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'user-comensal-1',
        email: 'valen@easyeat.com',
        role: 'COMENSAL',
        tenantId: null,
        tokenVersion: 1,
        mustChangePassword: false,
      },
      token: 'jwt-comensal',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    vi.spyOn(profileApi, 'getProfile').mockResolvedValue(sampleProfile as any);
    vi.spyOn(profileApi, 'getDietaryTaxonomy').mockResolvedValue([]);
    vi.spyOn(brandingApi, 'getDinerBranding').mockResolvedValue(sampleBranding);
    vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockResolvedValue(sampleCatalogResponse);
    vi.spyOn(dinerCatalogApi, 'getCatalogAvailability').mockResolvedValue({
      status: 200,
      etag: '"etag-1"',
      data: { availability: { 'dish-1': true, 'dish-2': true } },
    });
    vi.spyOn(catalogApi, 'getEstablishmentDishes').mockResolvedValue([]);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('emits exactly 1 /profile/me, 1 /branding, 1 /catalog, 0 /dishes, 0 /dietary-restrictions and 0 /availability at t=0ms', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });

    render(
      <ClientCacheProvider>
        <MemoryRouter initialEntries={['/catalogo']}>
          <CatalogPage />
        </MemoryRouter>
      </ClientCacheProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Bruschetta Clásica')).toBeInTheDocument();
    });

    expect(profileApi.getProfile).toHaveBeenCalledTimes(1);
    expect(brandingApi.getDinerBranding).toHaveBeenCalledTimes(1);
    expect(dinerCatalogApi.getEstablishmentCatalog).toHaveBeenCalledTimes(1);
    expect(catalogApi.getEstablishmentDishes).not.toHaveBeenCalled();
    expect(profileApi.getDietaryTaxonomy).not.toHaveBeenCalled();
    expect(dinerCatalogApi.getCatalogAvailability).not.toHaveBeenCalled();

    // First availability poll happens after 2500ms interval, not at t=0ms
    await act(async () => {
      vi.advanceTimersByTime(2500);
    });

    await waitFor(() => {
      expect(dinerCatalogApi.getCatalogAvailability).toHaveBeenCalledTimes(1);
    });
  });

  it('renders immediately from SWR cache without skeleton on revisit, preserving active category and open modal', async () => {
    let resolveRevalidation!: (v: typeof sampleCatalogResponse) => void;

    const Host: React.FC<{ show: boolean }> = ({ show }) => (
      <ClientCacheProvider>
        <MemoryRouter initialEntries={['/catalogo']}>
          {show ? <CatalogPage /> : <div>Otra vista</div>}
        </MemoryRouter>
      </ClientCacheProvider>
    );

    vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog')
      .mockResolvedValueOnce(sampleCatalogResponse)
      .mockImplementationOnce(() => new Promise((resolve) => { resolveRevalidation = resolve; }));

    const { rerender } = render(<Host show={true} />);
    await waitFor(() => {
      expect(screen.getByText('Bruschetta Clásica')).toBeInTheDocument();
    });

    rerender(<Host show={false} />);
    expect(screen.getByText('Otra vista')).toBeInTheDocument();

    rerender(<Host show={true} />);
    // Instant SWR render (< 100ms, no skeleton)
    expect(screen.getByText('Bruschetta Clásica')).toBeInTheDocument();
    expect(screen.queryByTestId('catalog-loading')).not.toBeInTheDocument();

    // Open dish detail modal while background revalidation is in flight
    fireEvent.click(screen.getByTestId('dish-card-dish-1'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await act(async () => {
      resolveRevalidation({
        ...sampleCatalogResponse,
        categories: [
          ...sampleCatalogResponse.categories,
        ],
      });
    });

    // Modal remains open after background revalidation settles
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('preserves cached catalog data on transient background revalidation error', async () => {
    const Host: React.FC<{ show: boolean }> = ({ show }) => (
      <ClientCacheProvider>
        <MemoryRouter initialEntries={['/catalogo']}>
          {show ? <CatalogPage /> : <div>Otra vista</div>}
        </MemoryRouter>
      </ClientCacheProvider>
    );

    vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog')
      .mockResolvedValueOnce(sampleCatalogResponse)
      .mockRejectedValueOnce(new Error('Network timeout'));

    const { rerender } = render(<Host show={true} />);
    await waitFor(() => {
      expect(screen.getByText('Bruschetta Clásica')).toBeInTheDocument();
    });

    rerender(<Host show={false} />);
    rerender(<Host show={true} />);

    // Cached data stays visible and no blocking error state replaces it
    await waitFor(() => {
      expect(dinerCatalogApi.getEstablishmentCatalog).toHaveBeenCalledTimes(2);
    });
    expect(screen.getByText('Bruschetta Clásica')).toBeInTheDocument();
    expect(screen.queryByTestId('catalog-error')).not.toBeInTheDocument();
  });

  it('preserves loading skeleton and Retry error screen on first visit without cache', async () => {
    let rejectFirst!: (err: Error) => void;
    vi.spyOn(dinerCatalogApi, 'getEstablishmentCatalog').mockImplementationOnce(
      () => new Promise((_, reject) => { rejectFirst = reject; })
    );

    render(
      <ClientCacheProvider>
        <MemoryRouter initialEntries={['/catalogo']}>
          <CatalogPage />
        </MemoryRouter>
      </ClientCacheProvider>
    );

    expect(screen.getByTestId('catalog-loading')).toBeInTheDocument();

    await act(async () => {
      rejectFirst(new Error('Server down'));
    });

    await waitFor(() => {
      expect(screen.getByTestId('catalog-error')).toBeInTheDocument();
    });
    expect(screen.getByTestId('catalog-retry-button')).toBeInTheDocument();
  });
});
