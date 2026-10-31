import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { StickyCategoryNav } from '@/components/catalog/StickyCategoryNav';

describe('StickyCategoryNav Component Tests (T032, US2, COM-4.1.1, FR-008)', () => {
  const categories = ['Entradas', 'Platos principales', 'Postres'];

  it('renders nothing when categories list is empty', () => {
    const { container } = render(<StickyCategoryNav categories={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders all category buttons and highlights the active category', () => {
    render(<StickyCategoryNav categories={categories} activeCategory="Platos principales" />);

    expect(screen.getByTestId('sticky-category-nav')).toBeInTheDocument();
    expect(screen.getByTestId('nav-category-Entradas')).toBeInTheDocument();
    expect(screen.getByTestId('nav-category-Platos principales')).toBeInTheDocument();
    expect(screen.getByTestId('nav-category-Postres')).toBeInTheDocument();

    const activeBtn = screen.getByTestId('nav-category-Platos principales');
    expect(activeBtn.className).toContain('bg-amber-600');
  });

  it('calls onSelectCategory and scrolls into view when a category button is clicked', () => {
    const onSelectCategory = vi.fn();
    const scrollIntoViewMock = vi.fn();

    // Mock getElementById
    const targetElement = document.createElement('div');
    targetElement.scrollIntoView = scrollIntoViewMock;
    vi.spyOn(document, 'getElementById').mockReturnValue(targetElement);

    render(
      <StickyCategoryNav
        categories={categories}
        activeCategory="Entradas"
        onSelectCategory={onSelectCategory}
      />
    );

    const postresBtn = screen.getByTestId('nav-category-Postres');
    fireEvent.click(postresBtn);

    expect(onSelectCategory).toHaveBeenCalledWith('Postres');
    expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
  });
});
