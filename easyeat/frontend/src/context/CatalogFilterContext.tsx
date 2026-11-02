import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';

export interface CatalogFilterState {
  selectedCategories: string[];
  minPrice: string;
  maxPrice: string;
  searchQuery: string;
}

export interface CatalogFilterContextType extends CatalogFilterState {
  isFilterActive: boolean;
  setIsFilterActive: (active: boolean) => void;
  toggleFilter: () => void;
  activeTenantId: string | null;
  setActiveTenantId: (tenantId: string | null) => void;
  resetFilter: () => void;
  toggleCategory: (category: string) => void;
  setMinPrice: (minPrice: string) => void;
  setMaxPrice: (maxPrice: string) => void;
  setSearchQuery: (query: string) => void;
  clearAllFilters: () => void;
}

export const CatalogFilterContext = createContext<CatalogFilterContextType | undefined>(undefined);

export interface CatalogFilterProviderProps {
  children: React.ReactNode;
  initialTenantId?: string | null;
}

const getStorageKey = (tenantId: string | null): string | null => {
  if (!tenantId) return null;
  return `easyeat_catalog_filters_${tenantId}`;
};

const loadFiltersFromSession = (tenantId: string | null): CatalogFilterState => {
  const defaultState: CatalogFilterState = {
    selectedCategories: [],
    minPrice: '',
    maxPrice: '',
    searchQuery: '',
  };

  const key = getStorageKey(tenantId);
  if (!key) return defaultState;

  try {
    const raw = sessionStorage.getItem(key);
    if (!raw) return defaultState;
    const parsed = JSON.parse(raw);
    return {
      selectedCategories: Array.isArray(parsed?.selectedCategories) ? parsed.selectedCategories : [],
      minPrice: typeof parsed?.minPrice === 'string' ? parsed.minPrice : '',
      maxPrice: typeof parsed?.maxPrice === 'string' ? parsed.maxPrice : '',
      searchQuery: typeof parsed?.searchQuery === 'string' ? parsed.searchQuery : '',
    };
  } catch {
    // Clarification 2026-11-02 Q3: Resilient cleanup of corrupt sessionStorage
    sessionStorage.removeItem(key);
    return defaultState;
  }
};

const saveFiltersToSession = (tenantId: string | null, state: CatalogFilterState) => {
  const key = getStorageKey(tenantId);
  if (!key) return;

  const isEmpty =
    state.selectedCategories.length === 0 &&
    state.minPrice === '' &&
    state.maxPrice === '' &&
    state.searchQuery === '';

  if (isEmpty) {
    sessionStorage.removeItem(key);
  } else {
    try {
      sessionStorage.setItem(key, JSON.stringify(state));
    } catch {
      // Ignore quota errors in constrained environments
    }
  }
};

export const CatalogFilterProvider: React.FC<CatalogFilterProviderProps> = ({
  children,
  initialTenantId = null,
}) => {
  // FR-019: Master toggle defaults to ON for diners
  const [isFilterActive, setIsFilterActive] = useState<boolean>(true);
  const [activeTenantId, setActiveTenantIdState] = useState<string | null>(initialTenantId);

  const initialLoaded = useRef(false);
  const [filterState, setFilterState] = useState<CatalogFilterState>(() =>
    loadFiltersFromSession(initialTenantId)
  );

  // Sync state whenever activeTenantId changes
  useEffect(() => {
    if (!initialLoaded.current) {
      initialLoaded.current = true;
      return;
    }
    const loaded = loadFiltersFromSession(activeTenantId);
    setFilterState(loaded);
  }, [activeTenantId]);

  const toggleCategory = useCallback((category: string) => {
    setFilterState((prev) => {
      const exists = prev.selectedCategories.includes(category);
      const nextCategories = exists
        ? prev.selectedCategories.filter((c) => c !== category)
        : [...prev.selectedCategories, category];
      const nextState = { ...prev, selectedCategories: nextCategories };
      saveFiltersToSession(activeTenantId, nextState);
      return nextState;
    });
  }, [activeTenantId]);

  const setMinPrice = useCallback((minPrice: string) => {
    setFilterState((prev) => {
      const nextState = { ...prev, minPrice };
      saveFiltersToSession(activeTenantId, nextState);
      return nextState;
    });
  }, [activeTenantId]);

  const setMaxPrice = useCallback((maxPrice: string) => {
    setFilterState((prev) => {
      const nextState = { ...prev, maxPrice };
      saveFiltersToSession(activeTenantId, nextState);
      return nextState;
    });
  }, [activeTenantId]);

  const setSearchQuery = useCallback((searchQuery: string) => {
    setFilterState((prev) => {
      const nextState = { ...prev, searchQuery };
      saveFiltersToSession(activeTenantId, nextState);
      return nextState;
    });
  }, [activeTenantId]);

  const clearAllFilters = useCallback(() => {
    const cleared: CatalogFilterState = {
      selectedCategories: [],
      minPrice: '',
      maxPrice: '',
      searchQuery: '',
    };
    const key = getStorageKey(activeTenantId);
    if (key) {
      sessionStorage.removeItem(key);
    }
    setFilterState(cleared);
  }, [activeTenantId]);

  const resetFilter = useCallback(() => {
    setIsFilterActive(true);
  }, []);

  const toggleFilter = useCallback(() => {
    setIsFilterActive((prev) => !prev);
  }, []);

  const setActiveTenantId = useCallback((newTenantId: string | null) => {
    setActiveTenantIdState((prevTenant) => {
      // FR-023: Navigating to a different restaurant resets toggle to ON
      if (prevTenant && newTenantId && prevTenant !== newTenantId) {
        setIsFilterActive(true);
        // Clear old filters in state for the new establishment
        const loaded = loadFiltersFromSession(newTenantId);
        setFilterState(loaded);
      }
      return newTenantId;
    });
  }, []);

  return (
    <CatalogFilterContext.Provider
      value={{
        isFilterActive,
        setIsFilterActive,
        toggleFilter,
        activeTenantId,
        setActiveTenantId,
        resetFilter,
        selectedCategories: filterState.selectedCategories,
        minPrice: filterState.minPrice,
        maxPrice: filterState.maxPrice,
        searchQuery: filterState.searchQuery,
        toggleCategory,
        setMinPrice,
        setMaxPrice,
        setSearchQuery,
        clearAllFilters,
      }}
    >
      {children}
    </CatalogFilterContext.Provider>
  );
};

export function useCatalogFilter(): CatalogFilterContextType {
  const context = useContext(CatalogFilterContext);
  if (!context) {
    throw new Error('useCatalogFilter must be used within a CatalogFilterProvider');
  }
  return context;
}

export default CatalogFilterContext;
