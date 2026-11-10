import React, { useState, useEffect, useMemo, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
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

  const { isAuthenticated } = useAuth();
  const { profile } = useProfile();
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
  const catalogHook = useCatalog();

  const [catalogData, setCatalogData] = useState<CatalogResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedDish, setSelectedDish] = useState<DishCatalogItem | null>(null);
  const [dishToConfirm, setDishToConfirm] = useState<EnrichedDish | null>(null);
  const [pendingQuantity, setPendingQuantity] = useState<number>(1);
  const [isCartOpen, setIsCartOpen] = useState<boolean>(false);
  const [activeCategory, setActiveCategory] = useState<string | undefined>(undefined);

  // Sync activeTenantId with CatalogFilterContext
  useEffect(() => {
    setActiveTenantId(tenant?.id || null);
  }, [tenant?.id, setActiveTenantId]);

  // Real-time stock-based dish availability polling (COM-4.1.2, FR-016)
  const { availability, isConnectionLost } = useRealtimeAvailability({
    tenantId: tenant?.id,
    enabled: Boolean(tenant?.id),
  });

  const activeRestrictionsCount = useMemo(() => {
    if (!isAuthenticated || !profile?.dietaryRestrictions) return 0;
    return profile.dietaryRestrictions.length;
  }, [isAuthenticated, profile]);

  // Missing tenant redirection (COM-1.5.1 / FR-004)
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

  // Load live categorized catalog from API when tenant is present
  const loadCatalog = async () => {
    if (!tenant?.id) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      const data = await dinerCatalogApi.getEstablishmentCatalog(tenant.id);
      setCatalogData(data);
      if (data.categories && data.categories.length > 0) {
        setActiveCategory(undefined);
      }
    } catch (err: any) {
      setError(err?.response?.data?.detail || 'Error al cargar el catálogo del establecimiento.');
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
  }, [tenant?.id]);

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

  const isAnyManualFilterActive =
    isCategoryFilterActive ||
    isLifestyleFilterActive ||
    isAllergenFilterActive ||
    minPrice.trim() !== '' ||
    maxPrice.trim() !== '' ||
    isQueryActive;

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
          // Dietary restriction filter (profile compliance)
          if (
            isAuthenticated &&
            activeRestrictionsCount > 0 &&
            isFilterActive &&
            dish.isCompliant === false
          ) {
            return false;
          }

          // Manual category filter: OR disjunction between selected categories
          if (isCategoryFilterActive && !selectedCategories.includes(group.category)) {
            return false;
          }

          // C-13: Lifestyles filter - mandatory inclusion (AND between selected lifestyles)
          if (isLifestyleFilterActive) {
            const dishLifestyles = dish.lifestyles || [];
            const hasAllLifestyles = selectedLifestyles.every((l) => dishLifestyles.includes(l));
            if (!hasAllLifestyles) {
              return false;
            }
          }

          // C-13: Allergens filter - exclusion (omit dish if it contains ANY selected allergen)
          if (isAllergenFilterActive) {
            const dishAllergens = dish.allergens || [];
            const hasAnyExcludedAllergen = selectedAllergens.some((a) => dishAllergens.includes(a));
            if (hasAnyExcludedAllergen) {
              return false;
            }
          }

          // Price range filter: closed inclusive interval [min, max]
          if (isPriceFilterValid) {
            const price = dish.price;
            if (minPriceNum !== null && !isNaN(minPriceNum) && price < minPriceNum) {
              return false;
            }
            if (maxPriceNum !== null && !isNaN(maxPriceNum) && price > maxPriceNum) {
              return false;
            }
          }

          // Text search filter: AND intersection
          if (isQueryActive && !matchesDishQuery(dish, searchQuery)) {
            return false;
          }

          // Out-of-stock dishes (INV-17, FR-021) remain visible under filters
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
        ? (availability[dish.id] !== undefined ? availability[dish.id] : dish.isAvailable !== false)
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
    }
  };

  const handleConfirmRisk = () => {
    if (dishToConfirm) {
      addItem(dishToConfirm, pendingQuantity, true);
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

  return (
    <WorkspaceLayout
      title={pageTitle}
      subtitle="Explorá nuestra carta y armá tu pedido en tiempo real"
    >
      <div className="space-y-6">
        {/* Top Header Actions */}
        <div className="flex items-center justify-between gap-4">
          {tenant ? (
            <button
              type="button"
              onClick={() => navigate('/descubrimiento')}
              data-testid="switch-establishment-btn"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-gray-700 bg-white border border-gray-200 hover:bg-gray-50 rounded-xl shadow-2xs transition-colors cursor-pointer"
            >
              <svg className="w-4 h-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
              <span>Cambiar local</span>
            </button>
          ) : (
            <div />
          )}
        </div>

        {/* Branded Restaurant Header (Contract 08, AS-9, Q7/A7, INV-009) */}
        <header
          data-testid="restaurant-branding-header"
          style={{
            backgroundColor: branding.secondaryColor || 'var(--brand-secondary, #065F46)',
            color: branding.secondaryContrastColor || 'var(--brand-secondary-contrast, #FFFFFF)',
          }}
          className="py-6 px-6 shadow-sm rounded-2xl transition-colors"
        >
          <div className="flex items-center gap-4">
            {branding.logoUrl && !logoFailed ? (
              <img
                src={branding.logoUrl}
                alt={branding.fantasyName || 'Logo'}
                onError={() => setLogoFailed(true)}
                data-testid="restaurant-branding-logo"
                className="h-16 w-16 rounded-2xl object-cover bg-white/10 p-1 border-2 border-white/20 shadow-sm"
              />
            ) : (
              <div className="h-16 w-16 rounded-2xl bg-white/20 flex items-center justify-center font-bold text-2xl border-2 border-white/20 shadow-sm">
                {(branding.fantasyName || pageTitle || 'E').charAt(0).toUpperCase()}
              </div>
            )}
            <div>
              <h1 className="text-2xl font-black tracking-tight" data-testid="restaurant-branding-name">
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

        {/* Guest Banner (FR-026, spec 003) */}
        {!isAuthenticated && (
          <div
            data-testid="guest-banner"
            className="p-4 bg-amber-50 border border-amber-200 rounded-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-amber-900"
          >
            <div className="flex items-center gap-3">
              <span className="text-2xl">ℹ️</span>
              <div>
                <h4 className="text-sm font-bold">Estás navegando como invitado</h4>
                <p className="text-xs text-amber-800 mt-0.5">
                  Iniciá sesión o registrate para activar el filtrado automático de platos según tus
                  requerimientos dietarios o médicos.
                </p>
              </div>
            </div>
            <div className="flex gap-2 w-full sm:w-auto">
              <Link
                to="/login"
                data-testid="guest-login-link"
                className="px-3 py-1.5 text-xs font-bold text-amber-900 bg-amber-200 hover:bg-amber-300 rounded-lg transition-colors text-center shrink-0"
              >
                Iniciar sesión
              </Link>
              <Link
                to="/register"
                data-testid="guest-register-link"
                className="px-3 py-1.5 text-xs font-bold text-white bg-amber-700 hover:bg-amber-800 rounded-lg transition-colors text-center shrink-0"
              >
                Registrarse
              </Link>
            </div>
          </div>
        )}

        {/* Operating Hours Banner (Spec 008, FR-003, FR-015) */}
        {catalogData?.establishment && (
          <OperatingHoursBanner
            isOpen={catalogData.establishment.isOpen}
            nextOpeningSchedule={catalogData.establishment.nextOpeningSchedule}
          />
        )}

        {/* Master Dietary Toggle (Spec 003) */}
        {isAuthenticated && activeRestrictionsCount > 0 && (
          <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-xs mb-6">
            <CatalogMasterToggle
              activeRestrictionsCount={activeRestrictionsCount}
              isFilterActive={isFilterActive}
              onToggle={setIsFilterActive}
            />
          </div>
        )}

        {/* Loading State */}
        {(isLoading || catalogHook.isLoading) && !catalogData && (
          <div
            data-testid="catalog-loading"
            className="py-16 text-center text-gray-500 space-y-3"
          >
            <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-amber-500 border-t-transparent" />
            <p className="text-sm font-medium">Cargando menú del comedor...</p>
          </div>
        )}
        {isLoading && catalogData && (
          <div
            data-testid="catalog-loading-skeleton"
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 animate-pulse"
          >
            {[1, 2, 3, 4, 5, 6].map((n) => (
              <div key={n} className="bg-white rounded-2xl h-72 border border-gray-100 shadow-2xs" />
            ))}
          </div>
        )}

        {/* Error State with Retry Button (Spec 008, COM-1.5.1 Edge Case, FR-013) */}
        {!isLoading && !catalogHook.isLoading && (error || catalogHook.error) && !catalogData && (
          <div
            data-testid="catalog-error"
            className="p-6 bg-red-50 border border-red-200 rounded-2xl text-center space-y-3"
          >
            <p className="text-sm font-semibold text-red-800">{error || catalogHook.error}</p>
            <button
              type="button"
              onClick={() => {
                loadCatalog();
                catalogHook.refetch();
              }}
              data-testid="catalog-retry-button"
              className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-xs font-bold rounded-lg shadow-xs cursor-pointer"
            >
              Reintentar
            </button>
          </div>
        )}
        {!isLoading && error && catalogData && (
          <div
            data-testid="catalog-error-state"
            className="p-6 bg-red-50 border border-red-200 rounded-2xl text-center space-y-3"
          >
            <p className="text-sm font-semibold text-red-800">{error}</p>
            <button
              type="button"
              onClick={() => {
                loadCatalog();
                catalogHook.refetch();
              }}
              data-testid="catalog-retry-button"
              className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-xs font-bold rounded-lg shadow-xs cursor-pointer"
            >
              Reintentar
            </button>
          </div>
        )}

        {/* Categorized Catalog Content with Sidebar Layout */}
        {!isLoading && !error && catalogData && (
          <>
            {/* Kitchen Connection Lost Warning Banner (COM-4.1.2 Edge Case, FR-019) */}
            {isConnectionLost && (
              <div
                data-testid="kitchen-connection-warning"
                className="bg-amber-50 border border-amber-300 text-amber-900 px-4 py-3 rounded-2xl flex items-center gap-3 text-xs sm:text-sm font-medium shadow-2xs"
              >
                <svg className="w-5 h-5 text-amber-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <span>Sin conexión con la cocina - reintentando...</span>
              </div>
            )}

            {/* In-Catalog Rapid Search Filter (COM-4.2.3, FR-020) */}
            <div className="pt-1">
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

            {/* Sticky Category Navigation Bar (FR-008) */}
            {categories.length > 0 && (
              <StickyCategoryNav
                categories={categories}
                activeCategory={activeCategory}
                onSelectCategory={setActiveCategory}
              />
            )}

            {/* Layout with Sidebar + Catalog Grid */}
            <div className="flex flex-col lg:flex-row gap-6 items-start">
              {/* Manual Filters Sidebar (COM-4.2.2, FR-016, FR-017, FR-022, C-13) */}
              {categories.length > 0 && (
                <ManualFiltersSidebar
                  categories={categories}
                  selectedCategories={selectedCategories}
                  onCategoryToggle={toggleCategory}
                  lifestyles={availableLifestyles}
                  selectedLifestyles={selectedLifestyles}
                  onLifestyleToggle={toggleLifestyle}
                  allergens={availableAllergens}
                  selectedAllergens={selectedAllergens}
                  onAllergenToggle={toggleAllergen}
                  minPrice={minPrice}
                  maxPrice={maxPrice}
                  onMinPriceChange={setMinPrice}
                  onMaxPriceChange={setMaxPrice}
                  onClearFilters={handleClearUnifiedFilters}
                />
              )}

              {/* Main Catalog View Container */}
              <div className="flex-1 w-full space-y-6">
                {catalogData.categories.length === 0 ? (
                  <div
                    data-testid="catalog-empty-store"
                    className="py-16 text-center text-gray-500 bg-white rounded-2xl border border-gray-200"
                  >
                    <p className="text-base font-semibold text-gray-700">No hay platos publicados</p>
                    <p className="text-xs text-gray-500 mt-1">
                      El menú de este establecimiento se encuentra en preparación.
                    </p>
                  </div>
                ) : filteredCategoryGroups.length === 0 ? (
                  !isAnyManualFilterActive && isAuthenticated && activeRestrictionsCount > 0 && isFilterActive ? (
                    <EmptyCatalogDietaryState onViewFullMenu={() => setIsFilterActive(false)} />
                  ) : (isCategoryFilterActive || isLifestyleFilterActive || isAllergenFilterActive || minPrice.trim() !== '' || maxPrice.trim() !== '') ? (
                    <EmptyFilterResultsNotice onClearFilters={handleClearUnifiedFilters} />
                  ) : (
                    <div
                      data-testid="catalog-empty-search"
                      className="py-16 text-center text-gray-500 bg-white rounded-2xl border border-gray-200 px-6 space-y-4"
                    >
                      <div className="mx-auto w-12 h-12 rounded-full bg-amber-50 flex items-center justify-center text-amber-600">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                          />
                        </svg>
                      </div>
                      <div className="space-y-1">
                        <p className="text-base font-bold text-gray-800">No se encontraron platos</p>
                        <p className="text-sm text-gray-500 max-w-md mx-auto">
                          No encontramos resultados para tu búsqueda. Verificá la ortografía o probá con otros términos o ingredientes.
                        </p>
                      </div>
                      <button
                        type="button"
                        data-testid="clear-empty-search-btn"
                        onClick={handleClearUnifiedFilters}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white text-xs font-semibold rounded-lg transition-colors cursor-pointer"
                      >
                        Limpiar filtros
                      </button>
                    </div>
                  )
                ) : (
                  /* Categorized Sections (FR-007, FR-009, FR-020, FR-022, AC-4.6, INV-17) */
                  <div className="space-y-10">
                    {filteredCategoryGroups.map((group) => (
                      <section
                        key={group.category}
                        id={`category-section-${encodeURIComponent(group.category)}`}
                        className="space-y-4 scroll-mt-20"
                      >
                        <div className="border-b border-gray-200 pb-2">
                          <h3 className="text-xl font-extrabold text-gray-900 tracking-tight">
                            {group.category}
                          </h3>
                          <p className="text-xs text-gray-500">
                            {group.dishes.length} {group.dishes.length === 1 ? 'plato disponible' : 'platos disponibles'}
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
                                  availability[dish.id] !== undefined ? availability[dish.id] : dish.isAvailable,
                              }}
                              isReadOnly={isReadOnly}
                              onSelectDish={setSelectedDish}
                              onAddToCart={handleAddToCart}
                              showWarnings={isAuthenticated && activeRestrictionsCount > 0 && !isFilterActive}
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

        {/* Fallback Flat Catalog Content (Spec 003 compatibility when useCatalog has dishes) */}
        {!isLoading && !catalogData && !catalogHook.isLoading && !catalogHook.error && catalogHook.dishes.length > 0 && (
          <>
            {displayedLegacyDishes.length === 0 ? (
              <EmptyCatalogDietaryState onViewFullMenu={() => setIsFilterActive(false)} />
            ) : (
              <div data-testid="dishes-grid" className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
                {displayedLegacyDishes.map((dish) => (
                  <DishCard
                    key={dish.id}
                    dish={dish}
                    onAddToCart={handleAddToCart}
                    showWarnings={isAuthenticated && activeRestrictionsCount > 0 && !isFilterActive}
                  />
                ))}
              </div>
            )}
          </>
        )}
      </div>

      {/* Informational Dish Detail Modal (FR-007, FR-012, FR-014, COM-6.1.1) */}
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

      {/* Preventive Risk Modal (FR-021, spec 003) */}
      <AddToCartRiskModal
        isOpen={!!dishToConfirm}
        dishName={dishToConfirm?.name || ''}
        conflictingRestrictions={dishToConfirm?.conflictingRestrictions || []}
        onConfirm={handleConfirmRisk}
        onCancel={handleCancelRisk}
      />

      {/* Floating Cart Button (COM-6.1.2, FR-005, FR-008, INV-6, INV-7) */}
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
