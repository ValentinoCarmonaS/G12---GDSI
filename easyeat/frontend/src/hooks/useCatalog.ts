import { useState, useEffect, useCallback } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { catalogApi } from '../api/catalogApi';
import { EnrichedDish } from '../types/catalog';

export const DEFAULT_DEV_TENANT_ID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';

export function useCatalog(overrideTenantId?: string) {
  const { tenantId: routeTenantId } = useParams<{ tenantId?: string }>();
  const [searchParams] = useSearchParams();
  const queryTenantId = searchParams.get('tenantId');

  const resolvedTenantId =
    overrideTenantId ||
    routeTenantId ||
    queryTenantId ||
    sessionStorage.getItem('activeTenantId') ||
    DEFAULT_DEV_TENANT_ID;

  const [dishes, setDishes] = useState<EnrichedDish[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchCatalog = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      sessionStorage.setItem('activeTenantId', resolvedTenantId);
      const data = await catalogApi.getEstablishmentDishes(resolvedTenantId);
      setDishes(data);
    } catch (err: any) {
      setError(err?.response?.data?.detail || 'Error al cargar el catálogo de platos.');
    } finally {
      setIsLoading(false);
    }
  }, [resolvedTenantId]);

  useEffect(() => {
    fetchCatalog();
  }, [fetchCatalog]);

  return {
    dishes,
    isLoading,
    error,
    tenantId: resolvedTenantId,
    refetch: fetchCatalog,
  };
}

export default useCatalog;
