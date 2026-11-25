import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Loader2, X, Store, Clock, AlertCircle } from 'lucide-react';
import { EstablishmentSummary } from '@/types/dinerCatalog';
import { dinerCatalogApi } from '@/api/dinerCatalogApi';
import { useTenant } from '@/hooks/useTenant';
import { useCart } from '@/hooks/useCart';
import { useClientCache } from '@/context/ClientCacheContext';
import { EstablishmentSwitchModal } from './EstablishmentSwitchModal';
import { StatusBadge } from '@/components/primitives/StatusBadge';
import { LoadingSkeleton } from '@/components/primitives/LoadingSkeleton';
import { EmptyState } from '@/components/primitives/EmptyState';
import { FeedbackState } from '@/components/primitives/FeedbackState';

export interface EstablishmentSearchBarProps {
  onSelect?: (establishment: EstablishmentSummary) => void;
  className?: string;
  autoFocus?: boolean;
}

export const EstablishmentSearchBar: React.FC<EstablishmentSearchBarProps> = ({
  onSelect,
  className = '',
  autoFocus = false,
}) => {
  const navigate = useNavigate();
  const { tenant, setTenant } = useTenant();
  const { items: cartItems, clearCart } = useCart();
  const cache = useClientCache();

  const [query, setQuery] = useState('');
  const [results, setResults] = useState<EstablishmentSummary[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [pendingEstablishment, setPendingEstablishment] = useState<EstablishmentSummary | null>(null);
  const [isSwitchModalOpen, setIsSwitchModalOpen] = useState(false);
  const [retryCounter, setRetryCounter] = useState(0);

  const containerRef = useRef<HTMLDivElement>(null);

  // Debounced search (300 ms, COM-1.5.1 CA 1) + complete listing (Fix C-01)
  useEffect(() => {
    const trimmed = query.trim();

    if (trimmed.length === 0) {
      let cancelled = false;
      const cacheKey = 'establishments:all';
      const cached = cache.get<EstablishmentSummary[]>(cacheKey);

      if (cached?.data) {
        setResults(Array.isArray(cached.data) ? cached.data : []);
        setSearchError(null);
        setIsLoading(false);
        setIsDropdownOpen(true);
      } else {
        setIsLoading(true);
        setSearchError(null);
      }

      cache
        .fetchDeduped(cacheKey, () => dinerCatalogApi.getAllEstablishments())
        .then((data) => {
          if (cancelled) return;
          setResults(Array.isArray(data) ? data : []);
          setSearchError(null);
          setIsDropdownOpen(true);
        })
        .catch(() => {
          if (cancelled) return;
          if (!cached?.data) {
            setResults([]);
            setSearchError('No pudimos cargar los establecimientos. Intentá nuevamente.');
            setIsDropdownOpen(true);
          }
        })
        .finally(() => {
          if (!cancelled) setIsLoading(false);
        });
      return () => {
        cancelled = true;
      };
    }

    if (trimmed.length < 3) {
      setResults([]);
      setSearchError(null);
      setIsDropdownOpen(false);
      setIsLoading(false);
      return;
    }

    let cancelled = false;
    setIsLoading(true);
    setSearchError(null);
    setIsDropdownOpen(true);

    const timer = setTimeout(async () => {
      try {
        const data = await dinerCatalogApi.searchEstablishments(trimmed);
        if (cancelled) return;
        setResults(Array.isArray(data) ? data : []);
        setIsDropdownOpen(true);
      } catch {
        if (cancelled) return;
        setResults([]);
        setSearchError('Ocurrió un error al buscar establecimientos. Revisá tu conexión.');
        setIsDropdownOpen(true);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }, 300);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query, retryCounter]);

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleRetry = useCallback(() => {
    setRetryCounter((prev) => prev + 1);
  }, []);

  const handleSelectEstablishment = (est: EstablishmentSummary) => {
    // Check if cart has items from different establishment (COM-1.5.1 CA 5)
    const hasItemsFromOtherEstablishment =
      cartItems.length > 0 &&
      (!tenant ||
        tenant.id !== est.id ||
        cartItems.some((item) => item.tenantId && item.tenantId !== est.id));

    if (hasItemsFromOtherEstablishment) {
      setPendingEstablishment(est);
      setIsSwitchModalOpen(true);
      setIsDropdownOpen(false);
      return;
    }

    applySelection(est);
  };

  const applySelection = (est: EstablishmentSummary) => {
    setTenant(est);
    setIsDropdownOpen(false);
    if (onSelect) {
      onSelect(est);
    } else {
      navigate('/catalogo');
    }
  };

  const handleConfirmSwitch = () => {
    if (pendingEstablishment) {
      clearCart();
      applySelection(pendingEstablishment);
    }
    setPendingEstablishment(null);
    setIsSwitchModalOpen(false);
  };

  const handleCancelSwitch = () => {
    setPendingEstablishment(null);
    setIsSwitchModalOpen(false);
  };

  const handleClear = () => {
    setQuery('');
    setResults([]);
    setSearchError(null);
    setIsDropdownOpen(false);
  };

  return (
    <div ref={containerRef} className={`relative w-full text-left ${className}`}>
      {/* Search Input Bar */}
      <div className="relative flex items-center w-full">
        <div className="absolute left-3.5 text-ee-text-secondary pointer-events-none flex items-center justify-center">
          {isLoading ? (
            <Loader2
              aria-hidden="true"
              className="w-5 h-5 animate-spin motion-reduce:animate-none text-ee-primary-border"
            />
          ) : (
            <Search aria-hidden="true" className="w-5 h-5" />
          )}
        </div>

        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => {
            if (results.length > 0 || searchError || isLoading) {
              setIsDropdownOpen(true);
            }
          }}
          placeholder="Buscar restaurante por nombre..."
          aria-label="Buscar restaurante por nombre"
          autoFocus={autoFocus}
          className="w-full min-h-[48px] pl-11 pr-12 py-3 bg-ee-surface-elevated border border-ee-border-strong rounded-ee-md shadow-ee-elevated hover:border-ee-text focus:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring text-ee-text placeholder:text-ee-text-secondary/75 text-base transition-colors motion-reduce:transition-none"
        />

        {query && (
          <button
            type="button"
            onClick={handleClear}
            className="absolute right-1.5 inline-flex min-h-[44px] min-w-[44px] items-center justify-center text-ee-text-secondary hover:text-ee-text rounded-ee-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-ee-focus-ring"
            aria-label="Limpiar búsqueda"
          >
            <X aria-hidden="true" className="w-4 h-4" />
          </button>
        )}
      </div>

      {/* Autocomplete Dropdown / Complete Listing (Fix C-01) */}
      {isDropdownOpen && (
        <div
          role="listbox"
          className="mt-3 w-full bg-ee-surface-elevated border border-ee-border-subtle rounded-ee-md shadow-ee-elevated overflow-hidden max-h-96 overflow-y-auto"
        >
          {isLoading ? (
            <div className="p-4">
              <LoadingSkeleton variant="text" lines={3} label="Buscando establecimientos..." />
            </div>
          ) : searchError ? (
            <div className="p-4">
              <FeedbackState
                variant="danger"
                icon={<AlertCircle className="h-4 w-4" />}
                message={searchError}
                actionLabel="Reintentar"
                onAction={handleRetry}
              />
            </div>
          ) : Array.isArray(results) && results.length > 0 ? (
            <div className="divide-y divide-ee-border-subtle">
              {results.map((est) => (
                <div
                  key={est.id}
                  role="option"
                  tabIndex={0}
                  aria-selected={tenant?.id === est.id}
                  onClick={() => handleSelectEstablishment(est)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      handleSelectEstablishment(est);
                    }
                  }}
                  className="flex items-center justify-between gap-3 px-4 py-3.5 min-h-[56px] hover:bg-ee-surface-subtle cursor-pointer transition-colors motion-reduce:transition-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-inset focus-visible:outline-ee-focus-ring"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-ee-sm bg-ee-surface-subtle border border-ee-border-subtle text-ee-primary-border">
                      <Store aria-hidden="true" className="w-5 h-5" />
                    </div>
                    <div className="min-w-0">
                      <h4 className="font-ee-display font-bold text-ee-text text-sm truncate">
                        {est.name}
                      </h4>
                      <div className="flex items-center gap-1.5 mt-0.5 text-xs text-ee-text-secondary font-ee-mono tabular-nums">
                        <Clock aria-hidden="true" className="w-3.5 h-3.5 shrink-0" />
                        <span>
                          {est.openingTime} - {est.closingTime}
                        </span>
                      </div>
                    </div>
                  </div>

                  <StatusBadge variant={est.isOpen ? 'success' : 'neutral'} className="shrink-0">
                    {est.isOpen ? 'Abierto' : 'Cerrado'}
                  </StatusBadge>
                </div>
              ))}
            </div>
          ) : (
            <div className="p-4">
              <EmptyState
                icon={<Store className="h-5 w-5" />}
                title={
                  query.trim().length === 0
                    ? 'Sin establecimientos activos'
                    : 'Sin coincidencias'
                }
                description={
                  query.trim().length === 0
                    ? 'No hay establecimientos activos en este momento.'
                    : 'No se encontraron establecimientos. Intentá modificar tu búsqueda.'
                }
                actionLabel={query.trim().length > 0 ? 'Limpiar búsqueda' : undefined}
                onAction={query.trim().length > 0 ? handleClear : undefined}
                actionVariant="outline"
                className="border-0 shadow-none py-6"
              />
            </div>
          )}
        </div>
      )}

      {/* Cart Clear Interceptor Confirmation Modal */}
      <EstablishmentSwitchModal
        isOpen={isSwitchModalOpen}
        targetEstablishment={pendingEstablishment}
        onConfirm={handleConfirmSwitch}
        onCancel={handleCancelSwitch}
      />
    </div>
  );
};
