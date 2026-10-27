import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useCatalog, DEFAULT_DEV_TENANT_ID } from '../../src/hooks/useCatalog';
import { catalogApi } from '../../src/api/catalogApi';

vi.mock('../../src/api/catalogApi', () => ({
  catalogApi: {
    getEstablishmentDishes: vi.fn(),
  },
}));

describe('useCatalog Hook (T053, US3)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <MemoryRouter initialEntries={['/catalogo']}>{children}</MemoryRouter>
  );

  it('falls back to DEFAULT_DEV_TENANT_ID when no route param or session activeTenantId exists', async () => {
    vi.mocked(catalogApi.getEstablishmentDishes).mockResolvedValueOnce([]);

    const { result } = renderHook(() => useCatalog(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.tenantId).toBe(DEFAULT_DEV_TENANT_ID);
    expect(catalogApi.getEstablishmentDishes).toHaveBeenCalledWith(DEFAULT_DEV_TENANT_ID);
    expect(sessionStorage.getItem('activeTenantId')).toBe(DEFAULT_DEV_TENANT_ID);
  });

  it('resolves tenantId from override argument and stores in sessionStorage', async () => {
    const customTenant = 'tenant-custom-123';
    vi.mocked(catalogApi.getEstablishmentDishes).mockResolvedValueOnce([
      {
        id: 'dish-1',
        tenantId: customTenant,
        name: 'Plato A',
        description: null,
        price: 4500,
        isActive: true,
        isCompliant: true,
        conflictingRestrictions: [],
      },
    ]);

    const { result } = renderHook(() => useCatalog(customTenant), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.tenantId).toBe(customTenant);
    expect(result.current.dishes).toHaveLength(1);
    expect(sessionStorage.getItem('activeTenantId')).toBe(customTenant);
  });

  it('handles API failure and sets error message', async () => {
    vi.mocked(catalogApi.getEstablishmentDishes).mockRejectedValueOnce({
      response: { data: { detail: 'Establecimiento no encontrado' } },
    });

    const { result } = renderHook(() => useCatalog(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.error).toBe('Establecimiento no encontrado');
    expect(result.current.dishes).toEqual([]);
  });
});
