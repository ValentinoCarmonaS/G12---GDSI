import React from 'react';
import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import {
  CatalogFilterProvider,
  useCatalogFilter,
} from '../../src/context/CatalogFilterContext';

describe('CatalogFilterContext (T054, US3, FR-019, FR-023)', () => {
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <CatalogFilterProvider initialTenantId="tenant-1">{children}</CatalogFilterProvider>
  );

  it('initializes with isFilterActive ON (true) by default (FR-019)', () => {
    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    expect(result.current.isFilterActive).toBe(true);
    expect(result.current.activeTenantId).toBe('tenant-1');
  });

  it('toggles filter state between ON and OFF', () => {
    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    act(() => {
      result.current.toggleFilter();
    });
    expect(result.current.isFilterActive).toBe(false);

    act(() => {
      result.current.toggleFilter();
    });
    expect(result.current.isFilterActive).toBe(true);
  });

  it('resets toggle to ON when restaurant/tenantId changes (FR-023)', () => {
    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    // Switch toggle to OFF during browsing in tenant-1
    act(() => {
      result.current.setIsFilterActive(false);
    });
    expect(result.current.isFilterActive).toBe(false);

    // Navigate to tenant-2
    act(() => {
      result.current.setActiveTenantId('tenant-2');
    });

    // Master toggle MUST reset to ON
    expect(result.current.isFilterActive).toBe(true);
    expect(result.current.activeTenantId).toBe('tenant-2');
  });

  it('resets filter explicitly via resetFilter()', () => {
    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    act(() => {
      result.current.setIsFilterActive(false);
    });
    expect(result.current.isFilterActive).toBe(false);

    act(() => {
      result.current.resetFilter();
    });
    expect(result.current.isFilterActive).toBe(true);
  });

  it('throws error when useCatalogFilter is used outside CatalogFilterProvider', () => {
    expect(() => renderHook(() => useCatalogFilter())).toThrow(
      /useCatalogFilter must be used within a CatalogFilterProvider/i
    );
  });
});
