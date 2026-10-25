import React, { useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { useCatalog } from '../hooks/useCatalog';
import { useAuth } from '../hooks/useAuth';
import { useProfile } from '../hooks/useProfile';
import { CatalogFilterProvider, useCatalogFilter } from '../context/CatalogFilterContext';
import { CartProvider } from '../context/CartContext';
import { useCart } from '../hooks/useCart';
import { CatalogMasterToggle } from '../components/catalog/CatalogMasterToggle';
import { DishCard } from '../components/catalog/DishCard';
import { EmptyCatalogDietaryState } from '../components/catalog/EmptyCatalogDietaryState';
import { AddToCartRiskModal } from '../components/cart/AddToCartRiskModal';
import { CartDrawer } from '../components/cart/CartDrawer';
import { EnrichedDish } from '../types/catalog';

export const CatalogView: React.FC = () => {
  const { dishes, isLoading, error, refetch } = useCatalog();
  const { isAuthenticated } = useAuth();
  const { profile } = useProfile();
  const { isFilterActive, setIsFilterActive } = useCatalogFilter();
  const { addItem, totalCount } = useCart();

  const [dishToConfirm, setDishToConfirm] = useState<EnrichedDish | null>(null);
  const [isCartOpen, setIsCartOpen] = useState<boolean>(false);

  const activeRestrictionsCount = useMemo(() => {
    if (!isAuthenticated || !profile?.dietaryRestrictions) return 0;
    return profile.dietaryRestrictions.length;
  }, [isAuthenticated, profile]);

  const displayedDishes = useMemo(() => {
    if (!isAuthenticated || activeRestrictionsCount === 0 || !isFilterActive) {
      return dishes;
    }
    // Strict conjunction: show dishes that are compliant
    return dishes.filter((dish) => dish.isCompliant !== false);
  }, [dishes, isFilterActive, isAuthenticated, activeRestrictionsCount]);

  const handleAddToCart = (dish: EnrichedDish) => {
    if (isAuthenticated && activeRestrictionsCount > 0 && dish.isCompliant === false) {
      // Intercept with preventive risk modal (FR-021)
      setDishToConfirm(dish);
    } else {
      addItem(dish, false);
    }
  };

  const handleConfirmRisk = () => {
    if (dishToConfirm) {
      addItem(dishToConfirm, true);
      setDishToConfirm(null);
    }
  };

  const handleCancelRisk = () => {
    setDishToConfirm(null);
  };

  return (
    <WorkspaceLayout
      title="Catálogo del Comedor"
      subtitle="Explorá nuestros platos y armá tu pedido"
    >
      <div className="space-y-6">
        {/* Floating / Header Cart Access Button */}
        <div className="flex justify-end">
          <button
            type="button"
            onClick={() => setIsCartOpen(true)}
            data-testid="open-cart-button"
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-amber-600 hover:bg-amber-700 text-white font-bold text-sm rounded-xl shadow-sm transition-all focus:ring-2 focus:ring-amber-500"
          >
            <svg
              className="w-5 h-5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
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
                className="ml-1 px-2 py-0.5 text-xs bg-white text-amber-800 rounded-full font-extrabold"
              >
                {totalCount}
              </span>
            )}
          </button>
        </div>

        {/* FR-026: Unauthenticated Guest Warning & Call to Action */}
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

        {/* Master Dietary Toggle (FR-023) */}
        {isAuthenticated && activeRestrictionsCount > 0 && (
          <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-xs">
            <CatalogMasterToggle
              activeRestrictionsCount={activeRestrictionsCount}
              isFilterActive={isFilterActive}
              onToggle={setIsFilterActive}
            />
          </div>
        )}

        {/* Loading State */}
        {isLoading && (
          <div
            data-testid="catalog-loading"
            className="py-16 text-center text-gray-500 space-y-3"
          >
            <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-amber-500 border-t-transparent" />
            <p className="text-sm font-medium">Cargando menú del comedor...</p>
          </div>
        )}

        {/* Error State */}
        {error && !isLoading && (
          <div
            data-testid="catalog-error"
            className="p-6 bg-red-50 border border-red-200 rounded-xl text-center space-y-3"
          >
            <p className="text-sm font-semibold text-red-800">{error}</p>
            <button
              type="button"
              onClick={() => refetch()}
              data-testid="catalog-retry-button"
              className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-xs font-bold rounded-lg shadow-xs"
            >
              Reintentar
            </button>
          </div>
        )}

        {/* Dishes Grid or Empty State */}
        {!isLoading && !error && (
          <>
            {dishes.length === 0 ? (
              <div
                data-testid="catalog-empty-store"
                className="py-16 text-center text-gray-500 bg-white rounded-xl border border-gray-200"
              >
                <p className="text-base font-semibold text-gray-700">No hay platos disponibles</p>
                <p className="text-xs text-gray-500 mt-1">El menú de este comedor se actualizará pronto.</p>
              </div>
            ) : displayedDishes.length === 0 ? (
              <EmptyCatalogDietaryState
                activeRestrictionsCount={activeRestrictionsCount}
                onViewFullMenu={() => setIsFilterActive(false)}
              />
            ) : (
              <div
                data-testid="dishes-grid"
                className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5"
              >
                {displayedDishes.map((dish) => (
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

      {/* Preventive Risk Modal (FR-021) */}
      <AddToCartRiskModal
        isOpen={!!dishToConfirm}
        dishName={dishToConfirm?.name || ''}
        conflictingRestrictions={dishToConfirm?.conflictingRestrictions || []}
        onConfirm={handleConfirmRisk}
        onCancel={handleCancelRisk}
      />

      {/* Cart Drawer with Persistent Inline Warnings (FR-022) */}
      <CartDrawer
        isOpen={isCartOpen}
        onClose={() => setIsCartOpen(false)}
        onCheckout={() => {
          setIsCartOpen(false);
          // Could navigate or trigger order flow
        }}
      />
    </WorkspaceLayout>
  );
};

export const CatalogPage: React.FC = () => {
  return (
    <CatalogFilterProvider>
      <CartProvider>
        <CatalogView />
      </CartProvider>
    </CatalogFilterProvider>
  );
};

export default CatalogPage;
