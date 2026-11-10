import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ManualFiltersSidebar } from '@/components/catalog/ManualFiltersSidebar';

describe('ManualFiltersSidebar Component Tests (T034, US4, COM-4.2.2, AC-4.1, AC-4.2, FR-016, FR-017, FR-022, EC-012, EC-014, EC-015, EC-016)', () => {
  const availableCategories = ['Entradas', 'Platos Principales', 'Postres', 'Bebidas'];

  it('renders available category checkboxes and price range inputs', () => {
    render(
      <ManualFiltersSidebar
        categories={availableCategories}
        selectedCategories={[]}
        onCategoryToggle={vi.fn()}
        minPrice=""
        maxPrice=""
        onMinPriceChange={vi.fn()}
        onMaxPriceChange={vi.fn()}
        onClearFilters={vi.fn()}
      />
    );

    availableCategories.forEach((category) => {
      expect(screen.getByLabelText(category)).toBeInTheDocument();
      expect(screen.getByLabelText(category)).not.toBeChecked();
    });

    expect(screen.getByLabelText(/precio mínimo/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/precio máximo/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /limpiar filtros/i })).toBeInTheDocument();
  });

  it('renders lifestyles and allergens sections when provided and triggers callbacks (C-13, FR-025, FR-026)', () => {
    const onLifestyleToggle = vi.fn();
    const onAllergenToggle = vi.fn();

    render(
      <ManualFiltersSidebar
        categories={availableCategories}
        selectedCategories={[]}
        onCategoryToggle={vi.fn()}
        lifestyles={['Vegano', 'Sin TACC / Celíaco']}
        selectedLifestyles={['Vegano']}
        onLifestyleToggle={onLifestyleToggle}
        allergens={['Lácteos', 'Maní']}
        selectedAllergens={['Maní']}
        onAllergenToggle={onAllergenToggle}
        minPrice=""
        maxPrice=""
        onMinPriceChange={vi.fn()}
        onMaxPriceChange={vi.fn()}
        onClearFilters={vi.fn()}
      />
    );

    const veganoCheckbox = screen.getByLabelText('Vegano');
    const celiacoCheckbox = screen.getByLabelText('Sin TACC / Celíaco');
    expect(veganoCheckbox).toBeChecked();
    expect(celiacoCheckbox).not.toBeChecked();

    fireEvent.click(celiacoCheckbox);
    expect(onLifestyleToggle).toHaveBeenCalledWith('Sin TACC / Celíaco');

    const lacteosCheckbox = screen.getByLabelText('Sin Lácteos');
    const maniCheckbox = screen.getByLabelText('Sin Maní');
    expect(maniCheckbox).toBeChecked();
    expect(lacteosCheckbox).not.toBeChecked();

    fireEvent.click(lacteosCheckbox);
    expect(onAllergenToggle).toHaveBeenCalledWith('Lácteos');
  });

  it('triggers onCategoryToggle callback when clicking category checkboxes (AC-4.1, EC-012)', () => {
    const onCategoryToggle = vi.fn();
    render(
      <ManualFiltersSidebar
        categories={availableCategories}
        selectedCategories={['Entradas']}
        onCategoryToggle={onCategoryToggle}
        minPrice=""
        maxPrice=""
        onMinPriceChange={vi.fn()}
        onMaxPriceChange={vi.fn()}
        onClearFilters={vi.fn()}
      />
    );

    const entradasCheckbox = screen.getByLabelText('Entradas');
    const postresCheckbox = screen.getByLabelText('Postres');

    expect(entradasCheckbox).toBeChecked();
    expect(postresCheckbox).not.toBeChecked();

    fireEvent.click(postresCheckbox);
    expect(onCategoryToggle).toHaveBeenCalledWith('Postres');

    fireEvent.click(entradasCheckbox);
    expect(onCategoryToggle).toHaveBeenCalledWith('Entradas');
  });

  it('triggers onMinPriceChange and onMaxPriceChange on numeric inputs (AC-4.2)', () => {
    const onMinPriceChange = vi.fn();
    const onMaxPriceChange = vi.fn();

    render(
      <ManualFiltersSidebar
        categories={availableCategories}
        selectedCategories={[]}
        onCategoryToggle={vi.fn()}
        minPrice="1000"
        maxPrice="5000"
        onMinPriceChange={onMinPriceChange}
        onMaxPriceChange={onMaxPriceChange}
        onClearFilters={vi.fn()}
      />
    );

    const minInput = screen.getByLabelText(/precio mínimo/i) as HTMLInputElement;
    const maxInput = screen.getByLabelText(/precio máximo/i) as HTMLInputElement;

    expect(minInput.value).toBe('1000');
    expect(maxInput.value).toBe('5000');

    fireEvent.change(minInput, { target: { value: '1500' } });
    expect(onMinPriceChange).toHaveBeenCalledWith('1500');

    fireEvent.change(maxInput, { target: { value: '6000' } });
    expect(onMaxPriceChange).toHaveBeenCalledWith('6000');
  });

  it('displays validation warning and reports invalid state when minPrice > maxPrice (FR-022, EC-014)', () => {
    render(
      <ManualFiltersSidebar
        categories={availableCategories}
        selectedCategories={[]}
        onCategoryToggle={vi.fn()}
        minPrice="5000"
        maxPrice="3000"
        onMinPriceChange={vi.fn()}
        onMaxPriceChange={vi.fn()}
        onClearFilters={vi.fn()}
      />
    );

    expect(
      screen.getByText(/el precio mínimo no puede superar al precio máximo/i)
    ).toBeInTheDocument();
  });

  it('does not display validation warning when minPrice <= maxPrice or when one is empty (EC-015, EC-016)', () => {
    const { rerender } = render(
      <ManualFiltersSidebar
        categories={availableCategories}
        selectedCategories={[]}
        onCategoryToggle={vi.fn()}
        minPrice="3000"
        maxPrice="3000"
        onMinPriceChange={vi.fn()}
        onMaxPriceChange={vi.fn()}
        onClearFilters={vi.fn()}
      />
    );

    expect(
      screen.queryByText(/el precio mínimo no puede superar al precio máximo/i)
    ).not.toBeInTheDocument();

    rerender(
      <ManualFiltersSidebar
        categories={availableCategories}
        selectedCategories={[]}
        onCategoryToggle={vi.fn()}
        minPrice="2000"
        maxPrice=""
        onMinPriceChange={vi.fn()}
        onMaxPriceChange={vi.fn()}
        onClearFilters={vi.fn()}
      />
    );

    expect(
      screen.queryByText(/el precio mínimo no puede superar al precio máximo/i)
    ).not.toBeInTheDocument();
  });

  it('triggers onClearFilters callback when clicking limpiar filtros button', () => {
    const onClearFilters = vi.fn();
    render(
      <ManualFiltersSidebar
        categories={availableCategories}
        selectedCategories={['Entradas']}
        onCategoryToggle={vi.fn()}
        minPrice="1000"
        maxPrice="5000"
        onMinPriceChange={vi.fn()}
        onMaxPriceChange={vi.fn()}
        onClearFilters={onClearFilters}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: /limpiar filtros/i }));
    expect(onClearFilters).toHaveBeenCalledTimes(1);
  });
});
