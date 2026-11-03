import React from 'react';

export interface EmptyFilterResultsNoticeProps {
  onClearFilters: () => void;
}

export const EmptyFilterResultsNotice: React.FC<EmptyFilterResultsNoticeProps> = ({
  onClearFilters,
}) => {
  return (
    <div
      data-testid="empty-filter-results-notice"
      className="p-8 text-center bg-gray-50/80 rounded-2xl border border-dashed border-gray-200 space-y-4 my-6"
    >
      <div className="w-12 h-12 mx-auto rounded-full bg-amber-50 text-amber-700 flex items-center justify-center text-xl">
        🔍
      </div>
      <div className="max-w-md mx-auto space-y-1">
        <h4 className="text-base font-bold text-gray-900">
          No se encontraron platos
        </h4>
        <p className="text-xs text-gray-600 leading-relaxed">
          No encontramos platos que coincidan con los filtros aplicados. Probá ajustando las categorías, el rango de precios o los términos de búsqueda.
        </p>
      </div>
      <div>
        <button
          type="button"
          data-testid="empty-filter-clear-btn"
          onClick={onClearFilters}
          className="inline-flex items-center justify-center px-4 py-2 text-xs font-bold text-white bg-amber-600 hover:bg-amber-700 active:bg-amber-800 rounded-xl shadow-xs transition-colors cursor-pointer"
        >
          Limpiar filtros
        </button>
      </div>
    </div>
  );
};
