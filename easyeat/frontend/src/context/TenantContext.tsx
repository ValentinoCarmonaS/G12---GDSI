import React, { createContext, useState, useEffect, useCallback, useMemo } from 'react';
import { EstablishmentSummary } from '@/types/dinerCatalog';

export const TENANT_STORAGE_KEY = 'easyeat_active_tenant';

export interface TenantContextType {
  tenant: EstablishmentSummary | null;
  setTenant: (tenant: EstablishmentSummary) => void;
  clearTenant: () => void;
  isLoading: boolean;
}

export const TenantContext = createContext<TenantContextType | undefined>(undefined);

export const TenantProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [tenant, setTenantState] = useState<EstablishmentSummary | null>(() => {
    try {
      const stored = sessionStorage.getItem(TENANT_STORAGE_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });
  const [isLoading, setIsLoading] = useState<boolean>(false);

  useEffect(() => {
    try {
      if (tenant) {
        sessionStorage.setItem(TENANT_STORAGE_KEY, JSON.stringify(tenant));
      } else {
        sessionStorage.removeItem(TENANT_STORAGE_KEY);
      }
    } catch (e) {
      console.error('Failed to sync active tenant to sessionStorage', e);
    }
  }, [tenant]);

  const setTenant = useCallback((newTenant: EstablishmentSummary) => {
    setTenantState(newTenant);
  }, []);

  const clearTenant = useCallback(() => {
    setTenantState(null);
  }, []);

  const value = useMemo(
    () => ({
      tenant,
      setTenant,
      clearTenant,
      isLoading,
    }),
    [tenant, setTenant, clearTenant, isLoading]
  );

  return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>;
};
