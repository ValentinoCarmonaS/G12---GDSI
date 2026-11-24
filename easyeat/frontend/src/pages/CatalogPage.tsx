import React, { useState, useEffect, useMemo, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Info, AlertTriangle, SlidersHorizontal, Search, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { useAuth } from '../hooks/useAuth';
import { useProfile } from '../hooks/useProfile';
import { useCatalog } from '../hooks/useCatalog';
import { TenantContext, TenantProvider } from '../context/TenantContext';
import { CatalogFilterContext, CatalogFilterProvider, useCatalogFilter } from '../context/CatalogFilterContext';
import { CartContext, CartProvider } from '../context/CartContext';
import { useCart } from '../hooks/useCart';
import { useRealtimeAvailability } from '../hooks/useRealtimeAvailability';
import { dinerCatalogApi } from '../api/dinerCatalogApi';
import type { CatalogResponse, DishCatalogItem } from '../types/dinerCatalog';
import type { EnrichedDish } from '../types/catalog';
import { StickyCategoryNav } from '../components/catalog/StickyCategoryNav';
import { OperatingHoursBanner } from '../components/catalog/OperatingHoursBanner';
import { DishCard } from '../components/catalog/DishCard';
import { DishDetailModal } from '../components/catalog/DishDetailModal';
import { CatalogMasterToggle } from '../components/catalog/CatalogMasterToggle';
import { EmptyCatalogDietaryState } from '../components/catalog/EmptyCatalogDietaryState';
import { AddToCartRiskModal } from '../components/cart/AddToCartRiskModal';
import { FloatingCartButton } from '../components/cart/FloatingCartButton';
import { CartDrawer } from '../components/cart/CartDrawer';
import {
  CatalogSearchFilter,
  matchesDishQuery,
  normalizeSearchTerm,
} from '../components/catalog/CatalogSearchFilter';
import { ManualFiltersSidebar } from '../components/catalog/ManualFiltersSidebar';
import { EmptyFilterResultsNotice } from '../components/catalog/EmptyFilterResultsNotice';
import { BrandThemeProvider, useBrandTheme } from '../components/common/BrandThemeProvider';
import { useClientCache } from '../context/ClientCacheContext';
import { resolveApiMediaUrl } from '../utils/mediaUtils';
import { BentoCard } from '../components/diner/BentoCard';
import { AccessibleDrawer } from '../components/diner/AccessibleDrawer';
import { LoadingSkeleton } from '../components/primitives/LoadingSkeleton';
import { FeedbackState } from '../components/primitives/FeedbackState';
import { Button } from '../components/primitives/Button';

const CatalogContent: React.FC = () => {
  const { branding } = useBrandTheme();
  const [logoFailed, setLogoFailed] = useState(false);

  useEffect(() => {
    setLogoFailed(false);
  }, [branding.logoUrl]);

  const navigate = useNavigate();
  const tenantContext = useContext(TenantContext);
  const tenant = tenantContext?.tenant;
  const isTenantLoading = tenantContext?.isLoading ?? false;

  const { isAuthenticated, user } = useAuth();
  const cache = useClientCache();
  const userScope = isAuthenticated && user?.id ? user.id : 'anon';
  const catalogCacheKey = tenant?.id ? `catalog:${tenant.id}:${userScope}` : '';
  const cachedCatalog = catalogCacheKey ? cache.get<CatalogResponse>(catalogCacheKey)?.data ?? null : null;

  const { profile } = useProfile({ enabled: isAuthenticated, loadTaxonomy: false });
  const {
    isFilterActive,
    setIsFilterActive,
    selectedCategories,
    selectedLifestyles,
    selectedAllergens,
    minPrice,
    maxPrice,
    searchQuery,
    toggleCategory,
    toggleLifestyle,
    toggleAllergen,
    setMinPrice,
    setMaxPrice,
    setSearchQuery,
    clearAllFilters,
    setActiveTenantId,
  } = useCatalogFilter();
  const { addItem } = useCart();
  const catalogHook = useCatalog(undefined, { enabled: !tenant?.id });

  const [catalogData, setCatalogData] = useState<CatalogResponse | null>(cachedCatalog);
  const [isLoading, setIsLoading] = useState<boolean>(Boolean(tenant?.id) && !cachedCatalog);
  const [error, setError] = useState<string | null>(null);
  const [selectedDish, setSelectedDish] = useState<DishCatalogItem | null>(null);
  const [dishToConfirm, setDishToConfirm] = useState<EnrichedDish | null>(null);
  const [pendingQuantity, setPendingQuantity] = useState<number>(1);
  const [isCartOpen, setIsCartOpen] = useState<boolean>(false);
  const [activeCategory, setActiveCategory] = useState<string | undefined>(undefined);
  const [isMobileFiltersOpen, setIsMobileFiltersOpen] = useState<boolean>(false);
  const [isDesktopFiltersOpen, setIsDesktopFiltersOpen] = useState<boolean>(true);
  const [cartAnnouncement, setCartAnnouncement] = useState<string>('');

  // Sync activeTenantId with CatalogFilterContext
  useEffect(() => {
    setActiveTenantId(tenant?.id || null);
  }, [tenant?.id, setActiveTenantId]);

  const initialAvailability = useMemo(() => {
    const map: Record<string, boolean> = {};
    catalogData?.categories?.forEach((cat) => {
      cat.dishes.forEach((dish) => {
        map[dish.id] = dish.isAvailable;
      });
    });
    return map;
  }, [catalogData]);

  // Real-time stock-based dish availability polling (deferred initial poll to avoid t=0ms duplicate)
  const { availability, isConnectionLost } = useRealtimeAvailability({
    tenantId: tenant?.id,
    enabled: Boolean(tenant?.id),
    initialAvailability,
    deferInitialPoll: true,
  });

  const activeRestrictionsCount = useMemo(() => {
    if (!isAuthenticated || !profile?.dietaryRestrictions) return 0;
    return profile.dietaryRestrictions.length;
  }, [isAuthenticated, profile]);

  // Missing tenant redirection
  useEffect(() => {
    if (
      !isTenantLoading &&
      !tenant &&
      isAuthenticated &&
      (!catalogHook.dishes || catalogHook.dishes.length === 0)
    ) {
      navigate('/descubrimiento', { replace: true });
    }
  }, [tenant, isTenantLoading, isAuthenticated, catalogHook.dishes, navigate]);

  const loadCatalog = async (forceRefresh = false) => {
    if (!tenant?.id) {
      setIsLoading(false);
      return;
    }
    const key = `catalog:${tenant.id}:${userScope}`;
    if (forceRefresh) {
      cache.invalidate(key);
    }
    const existing = cache.get<CatalogResponse>(key)?.data;
    if (existing) {
      setCatalogData(existing);
      setIsLoading(false);
    } else {
      setIsLoading(true);
    }
    setError(null);
    try {
      const data = await cache.fetchDeduped(key, () => dinerCatalogApi.getEstablishmentCatalog(tenant.id));
      setCatalogData(data);
      if (!existing && data.categories && data.categories.length > 0) {
        setActiveCategory(undefined);
      }
    } catch (err: any) {
      if (!existing) {
        setError(err?.response?.data?.detail || 'Error al cargar el catálogo del establecimiento.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (tenant?.id) {
      loadCatalog();
    } else {
      setIsLoading(false);
    }
  }, [tenant?.id, userScope]);

  const categories = useMemo(() => {
    if (!catalogData?.categories) return [];
    return catalogData.categories.map((c) => c.category);
  }, [catalogData]);

  const availableLifestyles = useMemo(() => {
    if (!catalogData?.categories) return [];
    const set = new Set<string>();
    catalogData.categories.forEach((cat) => {
      cat.dishes.forEach((d) => {
        d.lifestyles?.forEach((l) => set.add(l));
      });
    });
    return Array.from(set).sort();
  }, [catalogData]);

  const availableAllergens = useMemo(() => {
    if (!catalogData?.categories) return [];
    const set = new Set<string>();
    catalogData.categories.forEach((cat) => {
      cat.dishes.forEach((d) => {
        d.allergens?.forEach((a) => set.add(a));
      });
    });
    return Array.from(set).sort();
  }, [catalogData]);

  const isQueryActive = normalizeSearchTerm(searchQuery).length > 0;
  const isCategoryFilterActive = selectedCategories.length > 0;
  const isLifestyleFilterActive = selectedLifestyles.length > 0;
  const isAllergenFilterActive = selectedAllergens.length > 0;
  const minPriceNum = minPrice.trim() !== '' ? Number(minPrice) : null;
  const maxPriceNum = maxPrice.trim() !== '' ? Number(maxPrice) : null;
  const isPriceFilterValid =
    !(minPriceNum !== null && maxPriceNum !== null && !isNaN(minPriceNum) && !isNaN(maxPriceNum) && minPriceNum > maxPriceNum);

  const activeManualFiltersCount =
    selectedCategories.length +
    selectedLifestyles.length +
    selectedAllergens.length +
    (minPrice.trim() !== '' ? 1 : 0) +
    (maxPrice.trim() !== '' ? 1 : 0);

  const isAnyManualFilterActive = activeManualFiltersCount > 0 || isQueryActive;

  const filteredCategoryGroups = useMemo(() => {
    if (!catalogData?.categories) return [];

    return catalogData.categories
      .filter((group) => {
        if (activeCategory && activeCategory !== 'Todas') {
          return group.category === activeCategory;
        }
        return true;
      })
      .map((group) => {
        const matchingDishes = group.dishes.filter((dish) => {
          if (
            isAuthenticated &&
            activeRestrictionsCount > 0 &&
            isFilterActive &&
            dish.isCompliant === false
          ) {
            return false;
          }

          if (isCategoryFilterActive && !selectedCategories.includes(group.category)) {
            return false;
          }

          if (isLifestyleFilterActive) {
            const dishLifestyles = dish.lifestyles || [];
            const hasAllLifestyles = selectedLifestyles.every((l) => dishLifestyles.includes(l));
            if (!hasAllLifestyles) {
              return false;
            }
          }

          if (isAllergenFilterActive) {
            const dishAllergens = dish.allergens || [];
            const hasAnyExcludedAllergen = selectedAllergens.some((a) => dishAllergens.includes(a));
            if (hasAnyExcludedAllergen) {
              return false;
            }
          }

          if (isPriceFilterValid) {
            const price = dish.price;
            if (minPriceNum !== null && !isNaN(minPriceNum) && price < minPriceNum) {
              return false;
            }
            if (maxPriceNum !== null && !isNaN(maxPriceNum) && price > maxPriceNum) {
              return false;
            }
          }

          if (isQueryActive && !matchesDishQuery(dish, searchQuery)) {
            return false;
          }

          return true;
        });
        return {
          ...group,
          dishes: matchingDishes,
        };
      })
      .filter((group) => group.dishes.length > 0);
  }, [
    catalogData,
    activeCategory,
    searchQuery,
    isQueryActive,
    selectedCategories,
    isCategoryFilterActive,
    selectedLifestyles,
    isLifestyleFilterActive,
    selectedAllergens,
    isAllergenFilterActive,
    minPriceNum,
    maxPriceNum,
    isPriceFilterValid,
    isAuthenticated,
    activeRestrictionsCount,
    isFilterActive,
  ]);

  const isReadOnly = catalogData?.establishment?.isOpen === false;

  const displayedLegacyDishes = useMemo(() => {
    if (!isAuthenticated || activeRestrictionsCount === 0 || !isFilterActive) {
      return catalogHook.dishes;
    }
    return catalogHook.dishes.filter((dish) => dish.isCompliant !== false);
  }, [catalogHook.dishes, isFilterActive, isAuthenticated, activeRestrictionsCount]);

  const handleAddToCart = (dish: DishCatalogItem | EnrichedDish, quantity: number = 1) => {
    const isAvailable =
      'isAvailable' in dish
        ? availability[dish.id] !== undefined
          ? availability[dish.id]
          : dish.isAvailable !== false
        : dish.isActive !== false;

    if (isReadOnly || !isAvailable) return;

    const itemToAdd: EnrichedDish = {
      id: dish.id,
      tenantId: tenant?.id || ('tenantId' in dish ? dish.tenantId : ''),
      name: dish.name,
      price: dish.price,
      description: dish.description ?? null,
      isActive: true,
      isCompliant: dish.isCompliant,
      conflictingRestrictions: dish.conflictingRestrictions,
      availableStock: (dish as any).availableStock,
    };

    if (isAuthenticated && activeRestrictionsCount > 0 && dish.isCompliant === false) {
      setDishToConfirm(itemToAdd);
      setPendingQuantity(quantity);
    } else {
      addItem(itemToAdd, quantity, false);
      setCartAnnouncement(`${dish.name} agregado al carrito`);
    }
  };

  const handleConfirmRisk = () => {
    if (dishToConfirm) {
      addItem(dishToConfirm, pendingQuantity, true);
      setCartAnnouncement(`${dishToConfirm.name} agregado al carrito`);
      setDishToConfirm(null);
      setPendingQuantity(1);
    }
  };

  const handleCancelRisk = () => {
    setDishToConfirm(null);
    setPendingQuantity(1);
  };

  const handleClearUnifiedFilters = () => {
    clearAllFilters();
    setActiveCategory(undefined);
  };

  const pageTitle =
    (!branding.isDefaultFallback && branding.fantasyName ? branding.fantasyName : null) ||
    catalogData?.establishment?.name ||
    tenant?.name ||
    'Catálogo de Productos';

  const sidebarProps = {
    categories,
    selectedCategories,
    onCategoryToggle: toggleCategory,
    lifestyles: availableLifestyles,
    selectedLifestyles,
    onLifestyleToggle: toggleLifestyle,
    allergens: availableAllergens,
    selectedAllergens,
    onAllergenToggle: toggleAllergen,
    minPrice,
    maxPrice,
    onMinPriceChange: setMinPrice,
    onMaxPriceChange: setMaxPrice,
    onClearFilters: handleClearUnifiedFilters,
  };

  return (
    <WorkspaceLayout title={pageTitle}>
      {/* Live region for screen readers when adding items to cart */}
      <div className="sr-only" aria-live="polite" data-testid="catalog-cart-announcement">
        {cartAnnouncement}
      </div>

      <div className="space-y-6">
        {/* Top Header Actions */}
        <div className="flex items-center justify-between gap-4">
          {tenant ? (
            <button
              type="button"
              onClick={() => navigate('/descubrimiento')}
              data-testid="switch-establishment-btn"
              className="min-h-[44px] inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold text-[var(--ee-text)] bg-[var(--ee-surface-elevated)] border border-[var(--ee-border-subtle)] hover:border-[var(--ee-border-strong)] rounded-[var(--ee-radius-md)] transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
            >
              <ArrowLeft className="w-4 h-4 text-[var(--ee-text-secondary)]" aria-hidden="true" />
              <span>Cambiar local</span>
            </button>
          ) : (
            <div />
          )}
        </div>

        {/* Branded Restaurant Header (Focused Brand Accent Slot: header) */}
        <header
          data-testid="restaurant-branding-header"
          data-brand-slot="header"
          style={{
            backgroundColor: branding.secondaryColor || 'var(--brand-secondary, var(--ee-secondary))',
            color: branding.secondaryContrastColor || 'var(--brand-secondary-contrast, var(--ee-on-secondary))',
          }}
          className="py-6 px-6 rounded-[var(--ee-radius-lg)] border border-[var(--ee-border-subtle)] shadow-[var(--ee-shadow-elevated)] transition-colors"
        >
          <div className="flex items-center gap-4">
            {branding.logoUrl && !logoFailed ? (
              <img
                src={
                  branding.updatedAt && !branding.isDefaultFallback && !branding.logoUrl.startsWith('/assets/')
                    ? `${resolveApiMediaUrl(branding.logoUrl)}${resolveApiMediaUrl(branding.logoUrl).includes('?') ? '&' : '?'}v=${encodeURIComponent(branding.updatedAt)}`
                    : resolveApiMediaUrl(branding.logoUrl)
                }
                alt={branding.fantasyName || 'Logo'}
                onError={() => setLogoFailed(true)}
                data-testid="restaurant-branding-logo"
                className={`h-12 w-12 sm:h-14 sm:w-14 lg:h-16 lg:w-16 aspect-square rounded-[var(--ee-radius-md)] border border-[var(--ee-border-subtle)] shrink-0 ${
                  branding.logoUrl.startsWith('/assets/brand/')
                    ? 'object-cover bg-[#0b0b0d]'
                    : 'object-contain bg-[var(--ee-surface-elevated)] p-1'
                }`}
              />
            ) : (
              <div
                data-testid="restaurant-branding-logo-fallback"
                className="h-12 w-12 sm:h-14 sm:w-14 lg:h-16 lg:w-16 aspect-square rounded-[var(--ee-radius-md)] bg-[var(--ee-surface-elevated)] text-[var(--ee-text)] flex items-center justify-center font-bold text-xl sm:text-2xl border border-[var(--ee-border-subtle)] shrink-0"
              >
                {(branding.fantasyName || pageTitle || 'E').charAt(0).toUpperCase()}
              </div>
            )}
            <div className="min-w-0">
              <h1
                className="text-2xl font-bold tracking-tight truncate"
                data-testid="restaurant-branding-name"
              >
                {branding.fantasyName || pageTitle}
              </h1>
              {branding.description && (
                <p className="text-sm opacity-90 mt-1" data-testid="restaurant-branding-desc">
                  {branding.description}
                </p>
              )}
            </div>
          </div>
        </header>

        {/* Guest Banner */}
        {!isAuthenticated && (
          <div
            data-testid="guest-banner"
            className="p-4 bg-[var(--ee-info-bg)] border border-[var(--ee-info-border)] rounded-[var(--ee-radius-md)] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-[var(--ee-info-fg)]"
          >
            <div className="flex items-start sm:items-center gap-3">
              <Info className="w-5 h-5 shrink-0 mt-0.5 sm:mt-0" aria-hidden="true" />
              <div>
                <h2 className="text-sm font-bold">Estás navegando como invitado</h2>
                <p className="text-xs mt-0.5">
                  Iniciá sesión o registrate para activar el filtrado automático de platos según tus
                  requerimientos dietarios o médicos.
                </p>
              </div>
            </div>
            <div className="flex gap-2 w-full sm:w-auto">
              <Link
                to="/login"
                data-testid="guest-login-link"
                className="min-h-[44px] inline-flex items-center justify-center px-3.5 py-2 text-xs font-semibold text-[var(--ee-text)] bg-[var(--ee-surface-elevated)] border border-[var(--ee-border-strong)] rounded-[var(--ee-radius-sm)] transition-colors shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
              >
                Iniciar sesión
              </Link>
              <Link
                to="/register"
                data-testid="guest-register-link"
                className="min-h-[44px] inline-flex items-center justify-center px-3.5 py-2 text-xs font-semibold text-[var(--ee-on-primary)] bg-[var(--ee-primary)] border border-[var(--ee-primary-border)] rounded-[var(--ee-radius-sm)] transition-colors shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
              >
                Registrarse
              </Link>
            </div>
          </div>
        )}

        {/* Operating Hours Banner */}
        {catalogData?.establishment && (
          <OperatingHoursBanner
            isOpen={catalogData.establishment.isOpen}
            nextOpeningSchedule={catalogData.establishment.nextOpeningSchedule}
          />
        )}

        {/* Master Dietary Toggle (single BentoCard, no nested card) */}
        {isAuthenticated && activeRestrictionsCount > 0 && (
          <CatalogMasterToggle
            activeRestrictionsCount={activeRestrictionsCount}
            isFilterActive={isFilterActive}
            onToggle={setIsFilterActive}
          />
        )}

        {/* Loading State */}
        {(isLoading || catalogHook.isLoading) && !catalogData && (
          <div data-testid="catalog-loading" className="space-y-4 py-6">
            <LoadingSkeleton lines={3} />
            <p className="text-sm font-medium text-center text-[var(--ee-text-secondary)]">
              Cargando menú del comedor...
            </p>
          </div>
        )}
        {isLoading && catalogData && (
          <div
            data-testid="catalog-loading-skeleton"
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6"
          >
            {[1, 2, 3, 4, 5, 6].map((n) => (
              <BentoCard key={n} variant="elevated" padding="md" radius="lg">
                <LoadingSkeleton lines={4} />
              </BentoCard>
            ))}
          </div>
        )}

        {/* Error State with Retry Button */}
        {!isLoading && !catalogHook.isLoading && (error || catalogHook.error) && !catalogData && (
          <div data-testid="catalog-error" className="space-y-3">
            <FeedbackState
              variant="danger"
              title="No pudimos cargar el catálogo"
              message={error || catalogHook.error || 'Error al cargar el catálogo.'}
            />
            <div className="flex justify-center">
              <Button
                type="button"
                variant="danger"
                onClick={() => {
                  loadCatalog();
                  catalogHook.refetch();
                }}
                data-testid="catalog-retry-button"
                className="min-h-[44px]"
              >
                Reintentar
              </Button>
            </div>
          </div>
        )}
        {!isLoading && error && catalogData && (
          <div data-testid="catalog-error-state" className="space-y-3">
            <FeedbackState
              variant="danger"
              title="No pudimos actualizar el catálogo"
              message={error}
            />
            <div className="flex justify-center">
              <Button
                type="button"
                variant="danger"
                onClick={() => {
                  loadCatalog();
                  catalogHook.refetch();
                }}
                data-testid="catalog-retry-button"
                className="min-h-[44px]"
              >
                Reintentar
              </Button>
            </div>
          </div>
        )}

        {/* Categorized Catalog Content with Bento Sidebar Layout */}
        {!isLoading && !error && catalogData && (
          <>
            {/* Kitchen Connection Lost Warning Banner */}
            {isConnectionLost && (
              <div
                data-testid="kitchen-connection-warning"
                role="status"
                className="bg-[var(--ee-warning-bg)] border border-[var(--ee-warning-border)] text-[var(--ee-warning-fg)] px-4 py-3 rounded-[var(--ee-radius-md)] flex items-center gap-3 text-xs sm:text-sm font-medium"
              >
                <AlertTriangle className="w-5 h-5 shrink-0" aria-hidden="true" />
                <span>Sin conexión con la cocina - reintentando...</span>
              </div>
            )}

            {/* Search + Filter Controls Toolbar */}
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
              <div className="flex-1">
                <CatalogSearchFilter
                  value={searchQuery}
                  onChange={setSearchQuery}
                  onClear={() => setSearchQuery('')}
                  placeholder={
                    activeCategory && activeCategory !== 'Todas'
                      ? `Buscar en ${activeCategory} por plato o ingrediente...`
                      : 'Buscar por plato o ingrediente en todo el menú...'
                  }
                />
              </div>

              {categories.length > 0 && (
                <>
                  {/* Mobile Filters Drawer Trigger */}
                  <button
                    type="button"
                    data-testid="mobile-filters-trigger"
                    onClick={() => setIsMobileFiltersOpen(true)}
                    className="lg:hidden min-h-[44px] inline-flex items-center justify-center gap-2 px-4 py-2 text-xs font-semibold bg-[var(--ee-surface-elevated)] text-[var(--ee-text)] border border-[var(--ee-border-strong)] rounded-[var(--ee-radius-md)] cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
                  >
                    <SlidersHorizontal className="w-4 h-4" aria-hidden="true" />
                    <span>
                      Filtros{activeManualFiltersCount > 0 ? ` (${activeManualFiltersCount})` : ''}
                    </span>
                  </button>

                  {/* Desktop Collapsible Sidebar Toggle */}
                  <button
                    type="button"
                    data-testid="desktop-filters-toggle"
                    onClick={() => setIsDesktopFiltersOpen((prev) => !prev)}
                    className="hidden lg:inline-flex min-h-[44px] items-center justify-center gap-2 px-4 py-2 text-xs font-semibold bg-[var(--ee-surface-elevated)] text-[var(--ee-text)] border border-[var(--ee-border-subtle)] hover:border-[var(--ee-border-strong)] rounded-[var(--ee-radius-md)] cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
                  >
                    {isDesktopFiltersOpen ? (
                      <PanelLeftClose className="w-4 h-4" aria-hidden="true" />
                    ) : (
                      <PanelLeftOpen className="w-4 h-4" aria-hidden="true" />
                    )}
                    <span>
                      {isDesktopFiltersOpen ? 'Ocultar filtros' : 'Mostrar filtros'}
                      {activeManualFiltersCount > 0 ? ` (${activeManualFiltersCount})` : ''}
                    </span>
                  </button>
                </>
              )}
            </div>

            {/* Sticky Category Navigation Bar */}
            {categories.length > 0 && (
              <StickyCategoryNav
                categories={categories}
                activeCategory={activeCategory}
                onSelectCategory={setActiveCategory}
              />
            )}

            {/* Layout with Collapsible Desktop Sidebar + Catalog Grid */}
            <div className="flex flex-col lg:flex-row gap-6 items-start">
              {categories.length > 0 && isDesktopFiltersOpen && (
                <div className="hidden lg:block shrink-0">
                  <ManualFiltersSidebar {...sidebarProps} />
                </div>
              )}

              {/* Main Catalog View Container */}
              <div className="flex-1 w-full space-y-6">
                {catalogData.categories.length === 0 ? (
                  <BentoCard
                    variant="elevated"
                    padding="lg"
                    radius="lg"
                    data-testid="catalog-empty-store"
                    className="text-center"
                  >
                    <p className="text-base font-semibold text-[var(--ee-text)]">
                      No hay platos publicados
                    </p>
                    <p className="text-xs text-[var(--ee-text-secondary)] mt-1">
                      El menú de este establecimiento se encuentra en preparación.
                    </p>
                  </BentoCard>
                ) : filteredCategoryGroups.length === 0 ? (
                  !isAnyManualFilterActive &&
                  isAuthenticated &&
                  activeRestrictionsCount > 0 &&
                  isFilterActive ? (
                    <EmptyCatalogDietaryState onViewFullMenu={() => setIsFilterActive(false)} />
                  ) : isCategoryFilterActive ||
                    isLifestyleFilterActive ||
                    isAllergenFilterActive ||
                    minPrice.trim() !== '' ||
                    maxPrice.trim() !== '' ? (
                    <EmptyFilterResultsNotice onClearFilters={handleClearUnifiedFilters} />
                  ) : (
                    <BentoCard
                      variant="elevated"
                      padding="lg"
                      radius="lg"
                      data-testid="catalog-empty-search"
                      className="text-center space-y-4"
                    >
                      <div className="mx-auto w-12 h-12 rounded-full bg-[var(--ee-surface-subtle)] border border-[var(--ee-border-subtle)] flex items-center justify-center text-[var(--ee-text)]">
                        <Search className="w-5 h-5" aria-hidden="true" />
                      </div>
                      <div className="space-y-1">
                        <p className="text-base font-bold text-[var(--ee-text)]">
                          No se encontraron platos
                        </p>
                        <p className="text-sm text-[var(--ee-text-secondary)] max-w-md mx-auto">
                          No encontramos resultados para tu búsqueda. Verificá la ortografía o probá con otros términos o ingredientes.
                        </p>
                      </div>
                      <Button
                        type="button"
                        variant="primary"
                        data-testid="clear-empty-search-btn"
                        onClick={handleClearUnifiedFilters}
                        className="min-h-[44px]"
                      >
                        Limpiar filtros
                      </Button>
                    </BentoCard>
                  )
                ) : (
                  <div className="space-y-10">
                    {filteredCategoryGroups.map((group) => (
                      <section
                        key={group.category}
                        id={`category-section-${encodeURIComponent(group.category)}`}
                        className="space-y-4 scroll-mt-20"
                      >
                        <div className="border-b border-[var(--ee-border-subtle)] pb-2 flex items-baseline justify-between gap-2">
                          <h2 className="text-xl font-bold text-[var(--ee-text)] tracking-tight">
                            {group.category}
                          </h2>
                          <p className="text-xs font-ee-mono tabular-nums text-[var(--ee-text-secondary)]">
                            {group.dishes.length}{' '}
                            {group.dishes.length === 1 ? 'plato disponible' : 'platos disponibles'}
                          </p>
                        </div>

                        <div
                          data-testid={`category-grid-${group.category}`}
                          className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-6"
                        >
                          {group.dishes.map((dish) => (
                            <DishCard
                              key={dish.id}
                              dish={{
                                ...dish,
                                isAvailable:
                                  availability[dish.id] !== undefined
                                    ? availability[dish.id]
                                    : dish.isAvailable,
                              }}
                              isReadOnly={isReadOnly}
                              onSelectDish={setSelectedDish}
                              onAddToCart={handleAddToCart}
                              showWarnings={
                                isAuthenticated && activeRestrictionsCount > 0 && !isFilterActive
                              }
                            />
                          ))}
                        </div>
                      </section>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </>
        )}

        {/* Fallback Flat Catalog Content (Spec 003 compatibility) */}
        {!isLoading &&
          !catalogData &&
          !catalogHook.isLoading &&
          !catalogHook.error &&
          catalogHook.dishes.length > 0 && (
            <>
              {displayedLegacyDishes.length === 0 ? (
                <EmptyCatalogDietaryState onViewFullMenu={() => setIsFilterActive(false)} />
              ) : (
                <div
                  data-testid="dishes-grid"
                  className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5"
                >
                  {displayedLegacyDishes.map((dish) => (
                    <DishCard
                      key={dish.id}
                      dish={dish}
                      onAddToCart={handleAddToCart}
                      showWarnings={
                        isAuthenticated && activeRestrictionsCount > 0 && !isFilterActive
                      }
                    />
                  ))}
                </div>
              )}
            </>
          )}
      </div>

      {/* Mobile Filters AccessibleDrawer */}
      <AccessibleDrawer
        isOpen={isMobileFiltersOpen}
        onClose={() => setIsMobileFiltersOpen(false)}
        side="left"
        title="Filtros"
        testId="mobile-filters-drawer"
        closeButtonLabel="Cerrar filtros"
      >
        <ManualFiltersSidebar {...sidebarProps} embedded />
      </AccessibleDrawer>

      {/* Informational Dish Detail Modal */}
      <DishDetailModal
        dish={
          selectedDish
            ? {
                ...selectedDish,
                isAvailable:
                  availability[selectedDish.id] !== undefined
                    ? availability[selectedDish.id]
                    : selectedDish.isAvailable,
              }
            : null
        }
        isOpen={!!selectedDish}
        onClose={() => setSelectedDish(null)}
        isReadOnly={isReadOnly}
        nextOpeningSchedule={catalogData?.establishment?.nextOpeningSchedule}
        onAddToCart={(dish, quantity) => {
          handleAddToCart(dish, quantity);
        }}
      />

      {/* Preventive Risk Modal */}
      <AddToCartRiskModal
        isOpen={!!dishToConfirm}
        dishName={dishToConfirm?.name || ''}
        conflictingRestrictions={dishToConfirm?.conflictingRestrictions || []}
        onConfirm={handleConfirmRisk}
        onCancel={handleCancelRisk}
      />

      {/* Floating Cart Button */}
      <FloatingCartButton onClick={() => setIsCartOpen(true)} />

      {/* Cart Drawer */}
      <CartDrawer
        isOpen={isCartOpen}
        onClose={() => setIsCartOpen(false)}
        onCheckout={() => {
          setIsCartOpen(false);
          navigate('/checkout');
        }}
      />
    </WorkspaceLayout>
  );
};

export const CatalogView: React.FC = () => {
  const tenantContext = useContext(TenantContext);
  const tenant = tenantContext?.tenant;
  return (
    <BrandThemeProvider establishmentId={tenant?.id}>
      <CatalogContent />
    </BrandThemeProvider>
  );
};

export const CatalogPage: React.FC = () => {
  const existingTenantContext = useContext(TenantContext);
  const existingFilterContext = useContext(CatalogFilterContext);
  const existingCartContext = useContext(CartContext);

  let content = <CatalogView />;

  if (!existingFilterContext) {
    content = <CatalogFilterProvider>{content}</CatalogFilterProvider>;
  }

  if (!existingCartContext) {
    content = <CartProvider>{content}</CartProvider>;
  }

  if (!existingTenantContext) {
    content = <TenantProvider>{content}</TenantProvider>;
  }

  return content;
};

export default CatalogPage;
