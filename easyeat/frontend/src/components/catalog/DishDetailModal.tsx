import React, { useState } from 'react';
import type { DishCatalogItem } from '@/types/dinerCatalog';
import { AllergenWarningBadge } from './AllergenWarningBadge';

export interface DishDetailModalProps {
  dish: DishCatalogItem | null;
  isOpen: boolean;
  onClose: () => void;
  isReadOnly?: boolean;
  nextOpeningSchedule?: string | null;
}

export const DishDetailModal: React.FC<DishDetailModalProps> = ({
  dish,
  isOpen,
  onClose,
  isReadOnly = false,
  nextOpeningSchedule,
}) => {
  const [imageError, setImageError] = useState(false);

  if (!isOpen || !dish) {
    return null;
  }

  const isOutOfStock = dish.isAvailable === false && !isReadOnly;
  const hasConflicts = dish.isCompliant === false && (dish.conflictingRestrictions?.length ?? 0) > 0;

  return (
    <div
      data-testid="dish-detail-modal"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs transition-opacity animate-in fade-in"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-3xl max-w-lg w-full overflow-hidden shadow-2xl border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header Image */}
        <div className="relative w-full h-56 bg-gray-100 flex items-center justify-center overflow-hidden">
          {dish.photoUrl && !imageError ? (
            <img
              src={dish.photoUrl}
              alt={dish.name}
              onError={() => setImageError(true)}
              className="w-full h-full object-cover"
            />
          ) : (
            <div
              data-testid={`modal-placeholder-${dish.id}`}
              className="w-full h-full flex flex-col items-center justify-center bg-linear-to-br from-amber-50 to-orange-50 text-amber-800/40 p-4"
            >
              <svg
                className="w-16 h-16 mb-2"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"
                />
              </svg>
              <span className="text-xs font-semibold text-amber-900/60">EasyEat Gastronomía</span>
            </div>
          )}

          {/* Close button */}
          <button
            type="button"
            onClick={onClose}
            data-testid="modal-close-btn"
            className="absolute top-3 right-3 bg-black/40 hover:bg-black/60 text-white rounded-full p-2 transition-colors cursor-pointer"
            aria-label="Cerrar modal"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>

          {/* Availability badge */}
          {isOutOfStock && (
            <div className="absolute top-3 left-3 bg-gray-900/90 text-white text-xs font-bold px-3 py-1 rounded-full uppercase tracking-wider">
              Agotado
            </div>
          )}
        </div>

        {/* Modal Scrollable Content */}
        <div className="p-6 overflow-y-auto space-y-4">
          {/* Outside Hours Notice */}
          {isReadOnly && (
            <div
              data-testid="outside-hours-modal-notice"
              className="bg-amber-50 border border-amber-200 text-amber-900 p-3 rounded-xl text-xs flex items-center gap-2"
            >
              <svg className="w-4 h-4 shrink-0 text-amber-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>
                {nextOpeningSchedule
                  ? `Establecimiento cerrado. ${nextOpeningSchedule}. Catálogo en modo informativo.`
                  : 'Establecimiento fuera de su horario de atención. Catálogo en modo informativo.'}
              </span>
            </div>
          )}

          {/* Title and Price */}
          <div className="flex items-start justify-between gap-3">
            <div>
              <span className="text-2xs font-bold tracking-wider text-amber-700 uppercase">
                {dish.category}
              </span>
              <h3 className="text-xl font-bold text-gray-900">{dish.name}</h3>
            </div>
            <span className="text-xl font-extrabold text-amber-900 whitespace-nowrap">
              ${dish.price.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </span>
          </div>

          {/* Description */}
          {dish.description && (
            <p className="text-sm text-gray-700 leading-relaxed">
              {dish.description}
            </p>
          )}

          {/* Recipe Ingredients (COM-4.1.1, FR-007, FR-014) */}
          {dish.recipeIngredients && dish.recipeIngredients.length > 0 && (
            <div className="pt-2 border-t border-gray-100">
              <h4 className="text-xs font-bold text-gray-800 uppercase tracking-wider mb-1.5">
                Ingredientes
              </h4>
              <p className="text-xs text-gray-600 leading-relaxed">
                {dish.recipeIngredients.join(', ')}
              </p>
            </div>
          )}

          {/* Dietary Compliance */}
          {hasConflicts && (
            <div className="pt-2 border-t border-gray-100">
              <h4 className="text-xs font-bold text-red-800 uppercase tracking-wider mb-1.5">
                Advertencia Dietaria
              </h4>
              <AllergenWarningBadge conflicts={dish.conflictingRestrictions} size="sm" />
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="p-4 bg-gray-50 border-t border-gray-100 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2 bg-gray-200 hover:bg-gray-300 text-gray-800 text-sm font-semibold rounded-xl transition-colors cursor-pointer"
          >
            Cerrar
          </button>
        </div>
      </div>
    </div>
  );
};

export default DishDetailModal;
