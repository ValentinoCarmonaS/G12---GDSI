import React from 'react';

export interface EmptyCatalogDietaryStateProps {
  onViewFullMenu: () => void;
}

export const EmptyCatalogDietaryState: React.FC<EmptyCatalogDietaryStateProps> = ({
  onViewFullMenu,
}) => {
  return (
    <div
      data-testid="empty-catalog-dietary-state"
      className="bg-white rounded-2xl border border-dashed border-gray-300 p-8 text-center max-w-lg mx-auto my-12"
    >
      <div className="w-16 h-16 rounded-full bg-amber-100 text-amber-600 flex items-center justify-center mx-auto mb-4">
        <svg
          className="h-8 w-8"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.5}
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v3m0 0v3m0-3h3m-3 0H7"
          />
        </svg>
      </div>

      <h3 className="text-lg font-bold text-gray-900 mb-2">
        No encontramos platos que coincidan con tu dieta
      </h3>

      {/* FR-024 & SC-006 */}
      <p className="text-sm text-gray-600 mb-6 leading-relaxed">
        Este local no dispone de platos que satisfagan simultáneamente todas las restricciones de tu
        perfil. Podés desactivar temporalmente el filtro automático para ver la carta completa con
        advertencias visuales.
      </p>

      <button
        type="button"
        onClick={onViewFullMenu}
        data-testid="view-full-menu-button"
        className="inline-flex items-center justify-center px-5 py-2.5 bg-amber-600 hover:bg-amber-700 text-white text-sm font-semibold rounded-xl shadow-xs transition-colors"
      >
        Ver menú completo sin filtros de perfil
      </button>
    </div>
  );
};

export default EmptyCatalogDietaryState;
