import React, { useState, useEffect, useMemo, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { useAuth } from '../hooks/useAuth';
import { useProfile } from '../hooks/useProfile';
import { useCatalog } from '../hooks/useCatalog';
import { TenantContext, TenantProvider } from '../context/TenantContext';
import { CatalogFilterContext, CatalogFilterProvider, useCatalogFilter } from '../context/CatalogFilterContext';
import { CartProvider } from '../context/CartContext';
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
import { CartDrawer } from '../components/cart/CartDrawer';
import {
  CatalogSearchFilter,
  matchesDishQuery,
  normalizeSearchTerm,
} from '../components/catalog/CatalogSearchFilter';

export const CatalogView: React.FC = () => {
  const navigate = useNavigate();
  const tenantContext = useContext(TenantContext);
  const tenant = tenantContext?.tenant;
  const isTenantLoading = tenantContext?.isLoading ?? false;

  const { isAuthenticated } = useAuth();
  const { profile } = useProfile();
  const { isFilterActive, setIsFilterActive } = useCatalogFilter();
  const { addItem, totalCount } = useCart();
  const catalogHook = useCatalog();

  const [catalogData, setCatalogData] = useState<CatalogResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedDish, setSelectedDish] = useState<DishCatalogItem | null>(null);
  const [dishToConfirm, setDishToConfirm] = useState<EnrichedDish | null>(null);
  const [pendingQuantity, setPendingQuantity] = useState<number>(1);
  const [isCartOpen, setIsCartOpen] = useState<boolean>(false);
  const [activeCategory, setActiveCategory] = useState<string | undefined>(undefined);
  const [searchQuery, setSearchQuery] = useState<string>('');

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


  const isQueryActive = normalizeSearchTerm(searchQuery).length > 0;

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
          if (!isQueryActive) return true;
          return matchesDishQuery(dish, searchQuery);
        });
        return {
          ...group,
          dishes: matchingDishes,
        };
      })
      .filter((group) => group.dishes.length > 0);
  }, [catalogData, activeCategory, searchQuery, isQueryActive]);




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

  const pageTitle = catalogData?.establishment?.name || tenant?.name || 'Catálogo de Productos';

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

          {/* Floating Cart Button */}
          <button
            type="button"
            onClick={() => setIsCartOpen(true)}
            data-testid="open-cart-button"
            className="inline-flex items-center gap-2 px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white font-bold text-xs sm:text-sm rounded-xl shadow-xs transition-all focus:ring-2 focus:ring-amber-500 cursor-pointer"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"
              />
            </svg>
            <span>Ver Carrito</span>
            {totalCount > 0 && (
              <span
                data-testid="cart-badge-count"
                className="ml-1 px-1.5 py-0.5 text-2xs bg-white text-amber-800 rounded-full font-extrabold"
              >
                {totalCount}
              </span>
            )}
          </button>
        </div>

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
                className="flex-1 sm:flex-initial text-center px-3.5 py-1.5 bg-amber-600 hover:bg-amber-700 text-white text-xs font-semibold rounded-lg shadow-xs"
              >
                Iniciar sesión
              </Link>
              <Link
                to="/register"
                data-testid="guest-register-link"
                className="flex-1 sm:flex-initial text-center px-3.5 py-1.5 bg-white border border-amber-300 hover:bg-amber-100 text-amber-900 text-xs font-semibold rounded-lg shadow-xs"
              >
                Registrarse
              </Link>
            </div>
          </div>
        )}

        {/* Master Dietary Toggle (FR-023, spec 003) */}
        {isAuthenticated && activeRestrictionsCount > 0 && (
          <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-xs">
            <CatalogMasterToggle
              activeRestrictionsCount={activeRestrictionsCount}
              isFilterActive={isFilterActive}
              onToggle={setIsFilterActive}
            />
          </div>
        )}

        {/* Operating Hours Schedule Banner (FR-012, spec 008) */}
        {catalogData?.establishment && (
          <OperatingHoursBanner
            isOpen={catalogData.establishment.isOpen}
            nextOpeningSchedule={catalogData.establishment.nextOpeningSchedule}
          />
        )}

        {/* Loading State */}
        {(isLoading || catalogHook.isLoading) && !catalogData && (
          <div data-testid="catalog-loading" className="py-20 text-center text-gray-500 space-y-3">
            <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-amber-500 border-t-transparent" />
            <p className="text-sm font-medium">Cargando menú del comedor...</p>
          </div>
        )}

        {/* Error State */}
        {(error || catalogHook.error) && !isLoading && !catalogHook.isLoading && !catalogData && (
          <div data-testid="catalog-error" className="p-6 bg-red-50 border border-red-200 rounded-2xl text-center space-y-3">
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

        {/* Categorized Catalog Content (Spec 008) */}
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

            {/* Empty Categories View or Empty Search View */}
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
                {isQueryActive && (
                  <button
                    type="button"
                    data-testid="clear-empty-search-btn"
                    onClick={() => setSearchQuery('')}
                    className="inline-flex items-center gap-2 px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white text-xs font-semibold rounded-lg transition-colors cursor-pointer"
                  >
                    Limpiar búsqueda
                  </button>
                )}
              </div>
            ) : (
              /* Categorized Sections (FR-007, FR-009, FR-020, FR-022) */
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
                      className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6"
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
                        />
                      ))}
                    </div>
                  </section>
                ))}
              </div>
            )}
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

      {/* Cart Drawer */}
      <CartDrawer
        isOpen={isCartOpen}
        onClose={() => setIsCartOpen(false)}
        onCheckout={() => setIsCartOpen(false)}
      />
    </WorkspaceLayout>
  );
};

export const CatalogPage: React.FC = () => {
  const existingTenantContext = useContext(TenantContext);
  const existingFilterContext = useContext(CatalogFilterContext);

  let content = <CatalogView />;

  if (!existingFilterContext) {
    content = <CatalogFilterProvider>{content}</CatalogFilterProvider>;
  }

  content = <CartProvider>{content}</CartProvider>;

  if (!existingTenantContext) {
    content = <TenantProvider>{content}</TenantProvider>;
  }

  return content;
};

export default CatalogPage;
