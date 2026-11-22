import React from 'react';
import { X } from 'lucide-react';

export interface ManualFiltersSidebarProps {
  categories: string[];
  selectedCategories: string[];
  onCategoryToggle: (category: string) => void;
  lifestyles?: string[];
  selectedLifestyles?: string[];
  onLifestyleToggle?: (lifestyle: string) => void;
  allergens?: string[];
  selectedAllergens?: string[];
  onAllergenToggle?: (allergen: string) => void;
  minPrice: string;
  maxPrice: string;
  onMinPriceChange: (value: string) => void;
  onMaxPriceChange: (value: string) => void;
  onClearFilters: () => void;
  embedded?: boolean;
}

export const ManualFiltersSidebar: React.FC<ManualFiltersSidebarProps> = ({
  categories,
  selectedCategories,
  onCategoryToggle,
  lifestyles = [],
  selectedLifestyles = [],
  onLifestyleToggle,
  allergens = [],
  selectedAllergens = [],
  onAllergenToggle,
  minPrice,
  maxPrice,
  onMinPriceChange,
  onMaxPriceChange,
  onClearFilters,
  embedded = false,
}) => {
  const minNum = minPrice.trim() !== '' ? Number(minPrice) : null;
  const maxNum = maxPrice.trim() !== '' ? Number(maxPrice) : null;
  const isPriceRangeInvalid =
    minNum !== null && maxNum !== null && !isNaN(minNum) && !isNaN(maxNum) && minNum > maxNum;

  const activeFiltersCount =
    selectedCategories.length +
    selectedLifestyles.length +
    selectedAllergens.length +
    (minPrice.trim() !== '' ? 1 : 0) +
    (maxPrice.trim() !== '' ? 1 : 0);

  return (
    <aside
      aria-label="Filtros manuales de catálogo"
      data-testid="manual-filters-sidebar"
      className={
        embedded
          ? 'w-full space-y-6'
          : 'w-full lg:w-64 bg-[var(--ee-surface-elevated)] rounded-[var(--ee-radius-lg)] border border-[var(--ee-border-subtle)] p-5 shadow-[var(--ee-shadow-elevated)] space-y-6'
      }
    >
      <div className="flex items-center justify-between pb-3 border-b border-[var(--ee-border-subtle)]">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-bold text-[var(--ee-text)] tracking-wide uppercase">
            Filtros
          </h3>
          {activeFiltersCount > 0 && (
            <span
              data-testid="active-filters-count"
              className="inline-flex items-center justify-center min-w-[22px] h-[22px] px-1.5 rounded-full text-xs font-bold font-ee-mono tabular-nums bg-[var(--ee-primary)] text-[var(--ee-on-primary)] border border-[var(--ee-primary-border)]"
            >
              {activeFiltersCount}
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={onClearFilters}
          className="text-xs font-semibold text-[var(--ee-primary-border)] hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] rounded px-1 py-0.5 transition-colors cursor-pointer"
        >
          Limpiar filtros
        </button>
      </div>

      {/* Removable active filter chips */}
      {activeFiltersCount > 0 && (
        <div className="flex flex-wrap gap-1.5" data-testid="active-filter-chips">
          {selectedCategories.map((cat) => (
            <button
              key={`chip-cat-${cat}`}
              type="button"
              onClick={() => onCategoryToggle(cat)}
              aria-label={`Quitar filtro categoría ${cat}`}
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-[var(--ee-surface-subtle)] text-[var(--ee-text)] border border-[var(--ee-border-subtle)] hover:border-[var(--ee-border-strong)] cursor-pointer"
            >
              <span>{cat}</span>
              <X className="w-3 h-3" aria-hidden="true" />
            </button>
          ))}
          {selectedLifestyles.map((life) => (
            <button
              key={`chip-life-${life}`}
              type="button"
              onClick={() => onLifestyleToggle?.(life)}
              aria-label={`Quitar filtro dieta ${life}`}
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-[var(--ee-success-bg)] text-[var(--ee-success-fg)] border border-[var(--ee-success-border)] cursor-pointer"
            >
              <span>{life}</span>
              <X className="w-3 h-3" aria-hidden="true" />
            </button>
          ))}
          {selectedAllergens.map((alg) => (
            <button
              key={`chip-alg-${alg}`}
              type="button"
              onClick={() => onAllergenToggle?.(alg)}
              aria-label={`Quitar filtro sin ${alg}`}
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-[var(--ee-danger-bg)] text-[var(--ee-danger-fg)] border border-[var(--ee-danger-border)] cursor-pointer"
            >
              <span>Sin {alg}</span>
              <X className="w-3 h-3" aria-hidden="true" />
            </button>
          ))}
        </div>
      )}

      {/* Categories Filter (OR logic) */}
      <div className="space-y-3">
        <h4 className="text-xs font-semibold text-[var(--ee-text-secondary)] uppercase tracking-wider">
          Categorías
        </h4>
        <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
          {categories.length === 0 ? (
            <p className="text-xs text-[var(--ee-text-secondary)]">Sin categorías disponibles</p>
          ) : (
            categories.map((category) => {
              const isChecked = selectedCategories.includes(category);
              return (
                <label
                  key={category}
                  className="flex items-center gap-2.5 text-xs text-[var(--ee-text)] cursor-pointer select-none py-0.5"
                >
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={() => onCategoryToggle(category)}
                    className="w-4 h-4 rounded border-[var(--ee-border-strong)] accent-[var(--ee-primary-border)] focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
                  />
                  <span>{category}</span>
                </label>
              );
            })
          )}
        </div>
      </div>

      {/* Lifestyles / Diets Filter (AND inclusion logic - C-13) */}
      {lifestyles.length > 0 && (
        <div className="space-y-3">
          <h4 className="text-xs font-semibold text-[var(--ee-text-secondary)] uppercase tracking-wider">
            Estilos de vida / Dietas
          </h4>
          <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
            {lifestyles.map((lifestyle) => {
              const isChecked = selectedLifestyles.includes(lifestyle);
              return (
                <label
                  key={lifestyle}
                  className="flex items-center gap-2.5 text-xs text-[var(--ee-text)] cursor-pointer select-none py-0.5"
                >
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={() => onLifestyleToggle?.(lifestyle)}
                    className="w-4 h-4 rounded border-[var(--ee-border-strong)] accent-[var(--ee-success-border)] focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
                  />
                  <span>{lifestyle}</span>
                </label>
              );
            })}
          </div>
        </div>
      )}

      {/* Allergens to Exclude Filter (Exclusion logic - C-13) */}
      {allergens.length > 0 && (
        <div className="space-y-3">
          <h4 className="text-xs font-semibold text-[var(--ee-text-secondary)] uppercase tracking-wider">
            Excluir alérgenos
          </h4>
          <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
            {allergens.map((allergen) => {
              const isChecked = selectedAllergens.includes(allergen);
              return (
                <label
                  key={allergen}
                  className="flex items-center gap-2.5 text-xs text-[var(--ee-text)] cursor-pointer select-none py-0.5"
                >
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={() => onAllergenToggle?.(allergen)}
                    className="w-4 h-4 rounded border-[var(--ee-border-strong)] accent-[var(--ee-danger-border)] focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
                  />
                  <span>Sin {allergen}</span>
                </label>
              );
            })}
          </div>
        </div>
      )}

      {/* Price Range Filter (Closed Interval [min, max]) */}
      <div className="space-y-3">
        <h4 className="text-xs font-semibold text-[var(--ee-text-secondary)] uppercase tracking-wider">
          Rango de precio
        </h4>
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label
              htmlFor="filter-min-price"
              className="block text-xs font-medium text-[var(--ee-text-secondary)] mb-1"
            >
              Precio mínimo
            </label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-2.5 flex items-center text-xs text-[var(--ee-text-secondary)] pointer-events-none font-ee-mono">
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
                className={`w-full min-h-[40px] pl-6 pr-2 py-1.5 text-xs font-ee-mono tabular-nums rounded-[var(--ee-radius-sm)] bg-[var(--ee-surface-elevated)] text-[var(--ee-text)] border ${
                  isPriceRangeInvalid
                    ? 'border-[var(--ee-danger-border)]'
                    : 'border-[var(--ee-border-subtle)]'
                } focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] transition-colors`}
              />
            </div>
          </div>
          <div>
            <label
              htmlFor="filter-max-price"
              className="block text-xs font-medium text-[var(--ee-text-secondary)] mb-1"
            >
              Precio máximo
            </label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-2.5 flex items-center text-xs text-[var(--ee-text-secondary)] pointer-events-none font-ee-mono">
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
                className={`w-full min-h-[40px] pl-6 pr-2 py-1.5 text-xs font-ee-mono tabular-nums rounded-[var(--ee-radius-sm)] bg-[var(--ee-surface-elevated)] text-[var(--ee-text)] border ${
                  isPriceRangeInvalid
                    ? 'border-[var(--ee-danger-border)]'
                    : 'border-[var(--ee-border-subtle)]'
                } focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] transition-colors`}
              />
            </div>
          </div>
        </div>

        {isPriceRangeInvalid && (
          <p
            role="alert"
            className="text-xs font-medium text-[var(--ee-danger-fg)] bg-[var(--ee-danger-bg)] border border-[var(--ee-danger-border)] rounded-[var(--ee-radius-sm)] p-2"
          >
            El precio mínimo no puede superar al precio máximo
          </p>
        )}
      </div>
    </aside>
  );
};
