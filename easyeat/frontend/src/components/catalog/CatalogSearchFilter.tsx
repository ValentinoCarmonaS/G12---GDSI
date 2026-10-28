import React from 'react';

export interface CatalogSearchFilterProps {
  value: string;
  onChange: (query: string) => void;
  onClear: () => void;
  placeholder?: string;
  className?: string;
}

/**
 * Normalizes a text string by removing diacritics (accents), converting to lowercase,
 * and trimming leading/trailing whitespace (COM-4.2.3 CA 2, CA 3).
 */
export function normalizeSearchTerm(term: string): string {
  if (!term) return '';
  return term
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

/**
 * Evaluates whether a dish matches the search query.
 * Matches against dish name and recipe ingredient names (COM-4.2.3 CA 1, CA 2, CA 3).
 * Empty or whitespace-only queries match all dishes (CA 8).
 */
export function matchesDishQuery(
  dish: { name: string; recipeIngredients?: string[] },
  query: string
): boolean {
  const normalizedQuery = normalizeSearchTerm(query);
  if (!normalizedQuery) {
    return true;
  }

  const normalizedName = normalizeSearchTerm(dish.name);
  if (normalizedName.includes(normalizedQuery)) {
    return true;
  }

  if (dish.recipeIngredients && dish.recipeIngredients.length > 0) {
    return dish.recipeIngredients.some((ingredient) =>
      normalizeSearchTerm(ingredient).includes(normalizedQuery)
    );
  }

  return false;
}

export const CatalogSearchFilter: React.FC<CatalogSearchFilterProps> = ({
  value,
  onChange,
  onClear,
  placeholder = 'Buscar por plato o ingrediente...',
  className = '',
}) => {
  return (
    <div
      data-testid="catalog-search-filter"
      className={`relative flex items-center w-full ${className}`}
    >
      <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-gray-400">
        <svg
          className="w-4 h-4"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
          />
        </svg>
      </div>

      <input
        type="text"
        aria-label="Buscar en el catálogo"
        data-testid="catalog-search-input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full pl-10 pr-10 py-2.5 bg-white border border-gray-200 rounded-xl text-sm text-gray-900 placeholder-gray-400 shadow-2xs focus:outline-hidden focus:ring-2 focus:ring-amber-500/30 focus:border-amber-500 transition-all duration-150"
      />

      {value && (
        <button
          type="button"
          onClick={onClear}
          aria-label="Limpiar búsqueda"
          data-testid="catalog-search-clear"
          className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600 transition-colors cursor-pointer"
        >
          <svg
            className="w-4 h-4"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </button>
      )}
    </div>
  );
};

export default CatalogSearchFilter;
