import { createContext } from 'react';
import { EstablishmentSummary } from '@/types/dinerCatalog';

export interface TenantContextType {
  tenant: EstablishmentSummary | null;
  setTenant: (tenant: EstablishmentSummary) => void;
  clearTenant: () => void;
  isLoading: boolean;
}

export const TenantContext = createContext<TenantContextType | undefined>(undefined);
