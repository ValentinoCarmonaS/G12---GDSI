import React from 'react';

export interface ManualFiltersSidebarProps {
  categories: string[];
  selectedCategories: string[];
  onCategoryToggle: (category: string) => void;
  minPrice: string;
  maxPrice: string;
  onMinPriceChange: (value: string) => void;
  onMaxPriceChange: (value: string) => void;
  onClearFilters: () => void;
}

export const ManualFiltersSidebar: React.FC<ManualFiltersSidebarProps> = ({
  categories,
  selectedCategories,
  onCategoryToggle,
  minPrice,
  maxPrice,
  onMinPriceChange,
  onMaxPriceChange,
  onClearFilters,
}) => {
  const minNum = minPrice.trim() !== '' ? Number(minPrice) : null;
  const maxNum = maxPrice.trim() !== '' ? Number(maxPrice) : null;
  const isPriceRangeInvalid =
    minNum !== null && maxNum !== null && !isNaN(minNum) && !isNaN(maxNum) && minNum > maxNum;

  return (
    <aside
      aria-label="Filtros manuales de catálogo"
      data-testid="manual-filters-sidebar"
      className="w-full lg:w-64 bg-white rounded-2xl border border-gray-200/80 p-5 shadow-2xs space-y-6"
    >
      <div className="flex items-center justify-between pb-3 border-b border-gray-100">
        <h3 className="text-sm font-bold text-gray-900 tracking-wide uppercase">
          Filtros
        </h3>
        <button
          type="button"
          onClick={onClearFilters}
          className="text-xs font-semibold text-amber-700 hover:text-amber-900 hover:underline transition-colors cursor-pointer"
        >
          Limpiar filtros
        </button>
      </div>

      {/* Categories Filter (OR logic) */}
      <div className="space-y-3">
        <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wider">
          Categorías
        </h4>
        <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
          {categories.length === 0 ? (
            <p className="text-xs text-gray-400">Sin categorías disponibles</p>
          ) : (
            categories.map((category) => {
              const isChecked = selectedCategories.includes(category);
              return (
                <label
                  key={category}
                  className="flex items-center gap-2.5 text-xs text-gray-700 hover:text-gray-900 cursor-pointer select-none"
                >
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={() => onCategoryToggle(category)}
                    className="w-4 h-4 rounded text-amber-600 focus:ring-amber-500 border-gray-300 transition-colors"
                  />
                  <span>{category}</span>
                </label>
              );
            })
          )}
        </div>
      </div>

      {/* Price Range Filter (Closed Interval [min, max]) */}
      <div className="space-y-3">
        <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wider">
          Rango de precio
        </h4>
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label
              htmlFor="filter-min-price"
              className="block text-[11px] font-medium text-gray-500 mb-1"
            >
              Precio mínimo
            </label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-2.5 flex items-center text-xs text-gray-400 pointer-events-none">
                $
              </span>
              <input
                id="filter-min-price"
                type="number"
                min="0"
                step="any"
                value={minPrice}
                onChange={(e) => onMinPriceChange(e.target.value)}
                placeholder="0"
                className={`w-full pl-6 pr-2 py-1.5 text-xs rounded-xl border ${
                  isPriceRangeInvalid
                    ? 'border-red-400 focus:ring-red-400'
                    : 'border-gray-200 focus:ring-amber-500 focus:border-amber-500'
                } focus:outline-hidden transition-all`}
              />
            </div>
          </div>
          <div>
            <label
              htmlFor="filter-max-price"
              className="block text-[11px] font-medium text-gray-500 mb-1"
            >
              Precio máximo
            </label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-2.5 flex items-center text-xs text-gray-400 pointer-events-none">
                $
              </span>
              <input
                id="filter-max-price"
                type="number"
                min="0"
                step="any"
                value={maxPrice}
                onChange={(e) => onMaxPriceChange(e.target.value)}
                placeholder="Sin tope"
                className={`w-full pl-6 pr-2 py-1.5 text-xs rounded-xl border ${
                  isPriceRangeInvalid
                    ? 'border-red-400 focus:ring-red-400'
                    : 'border-gray-200 focus:ring-amber-500 focus:border-amber-500'
                } focus:outline-hidden transition-all`}
              />
            </div>
          </div>
        </div>

        {isPriceRangeInvalid && (
          <p
            role="alert"
            className="text-[11px] font-medium text-red-600 bg-red-50 border border-red-200 rounded-lg p-2"
          >
            El precio mínimo no puede superar al precio máximo
          </p>
        )}
      </div>
    </aside>
  );
};
