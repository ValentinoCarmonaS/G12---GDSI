import React from 'react';
import { Search, X } from 'lucide-react';

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
      <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-[var(--ee-text-secondary)]">
        <Search className="w-4 h-4" aria-hidden="true" />
      </div>

      <input
        type="text"
        aria-label="Buscar en el catálogo"
        data-testid="catalog-search-input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full min-h-[44px] pl-10 pr-11 py-2.5 bg-[var(--ee-surface-elevated)] border border-[var(--ee-border-subtle)] rounded-[var(--ee-radius-md)] text-sm text-[var(--ee-text)] placeholder:text-[var(--ee-text-secondary)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] focus:border-[var(--ee-border-strong)] transition-colors duration-150"
      />

      {value && (
        <button
          type="button"
          onClick={onClear}
          aria-label="Limpiar búsqueda"
          data-testid="catalog-search-clear"
          className="absolute inset-y-0 right-0 px-3 min-w-[44px] flex items-center justify-center text-[var(--ee-text-secondary)] hover:text-[var(--ee-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] rounded-r-[var(--ee-radius-md)] transition-colors cursor-pointer"
        >
          <X className="w-4 h-4" aria-hidden="true" />
        </button>
      )}
    </div>
  );
};

export default CatalogSearchFilter;
