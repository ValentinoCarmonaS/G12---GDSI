import React, { createContext, useContext, useState, useCallback } from 'react';

export interface CatalogFilterContextType {
  isFilterActive: boolean;
  setIsFilterActive: (active: boolean) => void;
  toggleFilter: () => void;
  activeTenantId: string | null;
  setActiveTenantId: (tenantId: string | null) => void;
  resetFilter: () => void;
}

export const CatalogFilterContext = createContext<CatalogFilterContextType | undefined>(undefined);

export interface CatalogFilterProviderProps {
  children: React.ReactNode;
  initialTenantId?: string | null;
}

export const CatalogFilterProvider: React.FC<CatalogFilterProviderProps> = ({
  children,
  initialTenantId = null,
}) => {
  // FR-019: Master toggle defaults to ON for diners
  const [isFilterActive, setIsFilterActive] = useState<boolean>(true);
  const [activeTenantId, setActiveTenantIdState] = useState<string | null>(initialTenantId);

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
