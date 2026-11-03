import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import React from 'react';
import {
  CatalogFilterProvider,
  useCatalogFilter,
} from '@/context/CatalogFilterContext';

describe('CatalogFilterContext Unit Tests (T036, US4, COM-4.2.2, INV-16, FR-020, FR-024, Clarification #1, #3)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <CatalogFilterProvider initialTenantId="tenant-123">
      {children}
    </CatalogFilterProvider>
  );

  it('initializes with empty filter state and default values', () => {
    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    expect(result.current.selectedCategories).toEqual([]);
    expect(result.current.minPrice).toBe('');
    expect(result.current.maxPrice).toBe('');
    expect(result.current.searchQuery).toBe('');
    expect(result.current.isFilterActive).toBe(true);
  });

  it('updates selected categories with toggleCategory', () => {
    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    act(() => {
      result.current.toggleCategory('Entradas');
    });

    expect(result.current.selectedCategories).toEqual(['Entradas']);

    act(() => {
      result.current.toggleCategory('Postres');
    });

    expect(result.current.selectedCategories).toEqual(['Entradas', 'Postres']);

    act(() => {
      result.current.toggleCategory('Entradas');
    });

    expect(result.current.selectedCategories).toEqual(['Postres']);
  });

  it('updates minPrice, maxPrice, and searchQuery', () => {
    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    act(() => {
      result.current.setMinPrice('1000');
      result.current.setMaxPrice('5000');
      result.current.setSearchQuery('milanesa');
    });

    expect(result.current.minPrice).toBe('1000');
    expect(result.current.maxPrice).toBe('5000');
    expect(result.current.searchQuery).toBe('milanesa');
  });

  it('persists filters in sessionStorage under easyeat_catalog_filters_{tenantId}', () => {
    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    act(() => {
      result.current.toggleCategory('Pizzas');
      result.current.setMinPrice('2000');
      result.current.setMaxPrice('8000');
      result.current.setSearchQuery('muzzarella');
    });

    const storedRaw = sessionStorage.getItem('easyeat_catalog_filters_tenant-123');
    expect(storedRaw).not.toBeNull();
    const stored = JSON.parse(storedRaw!);
    expect(stored.selectedCategories).toEqual(['Pizzas']);
    expect(stored.minPrice).toBe('2000');
    expect(stored.maxPrice).toBe('8000');
    expect(stored.searchQuery).toBe('muzzarella');
  });

  it('restores filters from sessionStorage on initial render for the active tenantId', () => {
    sessionStorage.setItem(
      'easyeat_catalog_filters_tenant-123',
      JSON.stringify({
        selectedCategories: ['Pastas'],
        minPrice: '3000',
        maxPrice: '7000',
        searchQuery: 'ravioles',
      })
    );

    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    expect(result.current.selectedCategories).toEqual(['Pastas']);
    expect(result.current.minPrice).toBe('3000');
    expect(result.current.maxPrice).toBe('7000');
    expect(result.current.searchQuery).toBe('ravioles');
  });

  it('cleans up corrupt or invalid JSON in sessionStorage gracefully (Clarification 2026-11-02 Q3)', () => {
    sessionStorage.setItem('easyeat_catalog_filters_tenant-123', 'invalid-json{{{');

    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    expect(result.current.selectedCategories).toEqual([]);
    expect(result.current.minPrice).toBe('');
    expect(result.current.maxPrice).toBe('');
    expect(result.current.searchQuery).toBe('');
    expect(sessionStorage.getItem('easyeat_catalog_filters_tenant-123')).toBeNull();
  });

  it('resets all filters simultaneously on clearAllFilters (Clarification #1, INV-16, FR-020)', () => {
    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    act(() => {
      result.current.toggleCategory('Bebidas');
      result.current.setMinPrice('500');
      result.current.setMaxPrice('1500');
      result.current.setSearchQuery('vino');
    });

    expect(sessionStorage.getItem('easyeat_catalog_filters_tenant-123')).not.toBeNull();

    act(() => {
      result.current.clearAllFilters();
    });

    expect(result.current.selectedCategories).toEqual([]);
    expect(result.current.minPrice).toBe('');
    expect(result.current.maxPrice).toBe('');
    expect(result.current.searchQuery).toBe('');
    expect(sessionStorage.getItem('easyeat_catalog_filters_tenant-123')).toBeNull();
  });

  it('resets filters when tenant changes to a different establishment (Clarification #3, INV-16)', () => {
    const { result } = renderHook(() => useCatalogFilter(), { wrapper });

    act(() => {
      result.current.toggleCategory('Carnes');
      result.current.setMinPrice('5000');
    });

    act(() => {
      result.current.setActiveTenantId('tenant-456');
    });

    expect(result.current.selectedCategories).toEqual([]);
    expect(result.current.minPrice).toBe('');
    expect(result.current.maxPrice).toBe('');
    expect(result.current.searchQuery).toBe('');
  });
});
