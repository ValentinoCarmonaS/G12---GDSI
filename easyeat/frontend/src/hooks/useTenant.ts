import { useContext } from 'react';
import { TenantContext, TenantContextType } from '@/context/TenantContext';

export function useTenant(): TenantContextType {
  const context = useContext(TenantContext);
  if (!context) {
    throw new Error('useTenant must be used within a TenantProvider');
  }
  return context;
}
