import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Loader2, X, Store, Clock } from 'lucide-react';
import { EstablishmentSummary } from '@/types/dinerCatalog';
import { dinerCatalogApi } from '@/api/dinerCatalogApi';
import { useTenant } from '@/hooks/useTenant';
import { useCart } from '@/hooks/useCart';
import { EstablishmentSwitchModal } from './EstablishmentSwitchModal';

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

  const [query, setQuery] = useState('');
  const [results, setResults] = useState<EstablishmentSummary[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [pendingEstablishment, setPendingEstablishment] = useState<EstablishmentSummary | null>(null);
  const [isSwitchModalOpen, setIsSwitchModalOpen] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);

  // Debounced search (300 ms, COM-1.5.1 CA 1)
  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 3) {
      setResults([]);
      setIsDropdownOpen(false);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    const timer = setTimeout(async () => {
      try {
        const data = await dinerCatalogApi.searchEstablishments(trimmed);
        setResults(data);
        setIsDropdownOpen(true);
      } catch (err) {
        console.error('Error searching establishments:', err);
        setResults([]);
        setIsDropdownOpen(true);
      } finally {
        setIsLoading(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [query]);

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

  const handleSelectEstablishment = (est: EstablishmentSummary) => {
    // Check if cart has items from different establishment (COM-1.5.1 CA 5)
    if (cartItems.length > 0 && (!tenant || tenant.id !== est.id)) {
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
    setIsDropdownOpen(false);
  };

  return (
    <div ref={containerRef} className={`relative w-full ${className}`}>
      {/* Search Input Bar */}
      <div className="relative flex items-center w-full">
        <div className="absolute left-4 text-gray-400 pointer-events-none flex items-center justify-center">
          {isLoading ? (
            <Loader2 className="w-5 h-5 animate-spin text-amber-500" />
          ) : (
            <Search className="w-5 h-5" />
          )}
        </div>

        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => {
            if (results.length > 0 && query.trim().length >= 3) {
              setIsDropdownOpen(true);
            }
          }}
          placeholder="Buscar restaurante por nombre..."
          autoFocus={autoFocus}
          className="w-full pl-12 pr-10 py-3.5 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-2xl shadow-sm hover:border-gray-300 dark:hover:border-gray-600 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:border-transparent text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 text-base transition-all"
        />

        {query && (
          <button
            type="button"
            onClick={handleClear}
            className="absolute right-4 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 p-1 rounded-full"
            aria-label="Limpiar búsqueda"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      {/* Autocomplete Dropdown */}
      {isDropdownOpen && query.trim().length >= 3 && (
        <div
          role="listbox"
          className="absolute z-40 w-full mt-2 bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 rounded-2xl shadow-xl overflow-hidden max-h-80 overflow-y-auto animate-fade-in"
        >
          {results.length > 0 ? (
            results.map((est) => (
              <div
                key={est.id}
                role="option"
                aria-selected={tenant?.id === est.id}
                onClick={() => handleSelectEstablishment(est)}
                className="flex items-center justify-between px-5 py-3.5 hover:bg-amber-50/70 dark:hover:bg-gray-700/60 cursor-pointer border-b border-gray-50 dark:border-gray-700/40 last:border-b-0 transition-colors"
              >
                <div className="flex items-center gap-3">
                  <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-xl text-gray-600 dark:text-gray-300">
                    <Store className="w-5 h-5 text-amber-600 dark:text-amber-400" />
                  </div>
                  <div>
                    <h4 className="font-semibold text-gray-900 dark:text-gray-100 text-sm">
                      {est.name}
                    </h4>
                    <div className="flex items-center gap-2 mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                      <Clock className="w-3.5 h-3.5" />
                      <span>
                        {est.openingTime} - {est.closingTime}
                      </span>
                    </div>
                  </div>
                </div>

                <div>
                  <span
                    className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold ${
                      est.isOpen
                        ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400'
                        : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
                    }`}
                  >
                    {est.isOpen ? 'Abierto' : 'Cerrado'}
                  </span>
                </div>
              </div>
            ))
          ) : (
            <div className="px-5 py-6 text-center text-sm text-gray-500 dark:text-gray-400">
              No se encontraron establecimientos. Intentá modificar tu búsqueda.
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

