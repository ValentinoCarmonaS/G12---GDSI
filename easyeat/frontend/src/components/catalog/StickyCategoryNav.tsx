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
      data-testid="sticky-category-nav"
      className="sticky top-0 z-20 bg-white/95 backdrop-blur-xs border-b border-gray-200 shadow-2xs py-2 px-4"
    >
      <div className="max-w-7xl mx-auto flex items-center gap-2 overflow-x-auto no-scrollbar">
        {showAllOption && (
          <button
            type="button"
            data-testid="nav-category-all"
            onClick={handleAllClick}
            className={`px-4 py-2 rounded-full text-xs sm:text-sm font-semibold whitespace-nowrap transition-all duration-200 cursor-pointer ${
              isAllActive
                ? 'bg-amber-600 text-white shadow-xs'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200 hover:text-gray-900'
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
              onClick={() => handleCategoryClick(category)}
              className={`px-4 py-2 rounded-full text-xs sm:text-sm font-semibold whitespace-nowrap transition-all duration-200 cursor-pointer ${
                isActive
                  ? 'bg-amber-600 text-white shadow-xs'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200 hover:text-gray-900'
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
