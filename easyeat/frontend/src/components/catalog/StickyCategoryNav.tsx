import React from 'react';

export interface StickyCategoryNavProps {
  categories: string[];
  activeCategory?: string;
  onSelectCategory?: (category: string | undefined) => void;
  showAllOption?: boolean;
}

export const StickyCategoryNav: React.FC<StickyCategoryNavProps> = ({
  categories,
  activeCategory,
  onSelectCategory,
  showAllOption = true,
}) => {
  if (!categories || categories.length === 0) {
    return null;
  }

  const handleAllClick = () => {
    if (onSelectCategory) {
      onSelectCategory(undefined);
    }
  };

  const handleCategoryClick = (category: string) => {
    if (onSelectCategory) {
      if (activeCategory === category) {
        onSelectCategory(undefined);
      } else {
        onSelectCategory(category);
      }
    }
    const element = document.getElementById(`category-section-${encodeURIComponent(category)}`);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const isAllActive = !activeCategory || activeCategory === 'Todas';

  return (
    <nav
      aria-label="Categorías del menú"
      data-testid="sticky-category-nav"
      className="sticky top-0 z-20 bg-[var(--ee-surface-elevated)] border border-[var(--ee-border-subtle)] rounded-[var(--ee-radius-md)] py-2 px-3 shadow-[var(--ee-shadow-elevated)]"
    >
      <div className="flex items-center gap-2 overflow-x-auto no-scrollbar">
        {showAllOption && (
          <button
            type="button"
            data-testid="nav-category-all"
            data-brand-slot={isAllActive ? 'category' : undefined}
            aria-pressed={isAllActive}
            onClick={handleAllClick}
            style={
              isAllActive
                ? {
                    backgroundColor: 'var(--brand-primary, var(--ee-primary))',
                    color: 'var(--brand-primary-contrast, var(--ee-on-primary))',
                  }
                : undefined
            }
            className={`min-h-[44px] px-4 py-2 rounded-full text-xs sm:text-sm font-semibold whitespace-nowrap transition-colors duration-150 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] ${
              isAllActive
                ? 'border border-[var(--ee-border-strong)]'
                : 'bg-[var(--ee-surface-subtle)] text-[var(--ee-text)] hover:bg-[var(--ee-neutral-bg)] border border-transparent'
            }`}
          >
            Todas
          </button>
        )}
        {categories.map((category) => {
          const isActive = activeCategory === category;
          return (
            <button
              key={category}
              type="button"
              data-testid={`nav-category-${category}`}
              data-brand-slot={isActive ? 'category' : undefined}
              aria-pressed={isActive}
              onClick={() => handleCategoryClick(category)}
              style={
                isActive
                  ? {
                      backgroundColor: 'var(--brand-primary, var(--ee-primary))',
                      color: 'var(--brand-primary-contrast, var(--ee-on-primary))',
                    }
                  : undefined
              }
              className={`min-h-[44px] px-4 py-2 rounded-full text-xs sm:text-sm font-semibold whitespace-nowrap transition-colors duration-150 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] ${
                isActive
                  ? 'border border-[var(--ee-border-strong)]'
                  : 'bg-[var(--ee-surface-subtle)] text-[var(--ee-text)] hover:bg-[var(--ee-neutral-bg)] border border-transparent'
              }`}
            >
              {category}
            </button>
          );
        })}
      </div>
    </nav>
  );
};

export default StickyCategoryNav;
