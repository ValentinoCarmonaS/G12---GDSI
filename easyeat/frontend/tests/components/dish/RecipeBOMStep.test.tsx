import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RecipeBOMStep } from '@/components/dish/RecipeBOMStep';
import type { RecipeIngredientItem } from '@/types/dish';

// Mock IngredientSelectorModal to simplify component testing
vi.mock('@/components/dish/IngredientSelectorModal', () => ({
  IngredientSelectorModal: ({ isOpen, onSelectIngredient }: any) => {
    if (!isOpen) return null;
    return (
      <div data-testid="mock-ingredient-modal">
        <button
          onClick={() =>
            onSelectIngredient({
              id: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
              name: 'Arroz Carnaroli',
              category: 'Dry Goods',
              unitOfMeasure: 'g',
            })
          }
        >
          Add Arroz
        </button>
        <button
          onClick={() =>
            onSelectIngredient({
              id: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02',
              name: 'Huevos de Campo',
              category: 'Dairy',
              unitOfMeasure: 'u',
            })
          }
        >
          Add Huevos
        </button>
      </div>
    );
  },
}));

describe('RecipeBOMStep Component (T039, CHF-2.2.2, FR-008, FR-009, FR-010)', () => {
  const mockOnSaveRecipe = vi.fn();
  const mockOnRemoveIngredient = vi.fn();

  const sampleRecipe: RecipeIngredientItem[] = [
    {
      ingredientId: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
      ingredientName: 'Arroz Carnaroli',
      quantity: 150.5,
      unitOfMeasure: 'g',
      isIngredientActive: true,
    },
    {
      ingredientId: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02',
      ingredientName: 'Huevos de Campo',
      quantity: 2,
      unitOfMeasure: 'u',
      isIngredientActive: true,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders initial recipe ingredients table with units, quantities and actions', () => {
    render(
      <RecipeBOMStep
        dishId="d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"
        initialRecipe={sampleRecipe}
        onSaveRecipe={mockOnSaveRecipe}
        onRemoveIngredient={mockOnRemoveIngredient}
      />
    );

    expect(screen.getByText('Arroz Carnaroli')).toBeInTheDocument();
    expect(screen.getByText('Huevos de Campo')).toBeInTheDocument();
    expect(screen.getByDisplayValue('150.5')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2')).toBeInTheDocument();
    expect(screen.getByText('g')).toBeInTheDocument();
    expect(screen.getByText('u')).toBeInTheDocument();
  });

  it('allows adding an ingredient from selector modal', async () => {
    const user = userEvent.setup();
    render(
      <RecipeBOMStep
        dishId="d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"
        initialRecipe={[]}
        onSaveRecipe={mockOnSaveRecipe}
        onRemoveIngredient={mockOnRemoveIngredient}
      />
    );

    expect(screen.getByText(/no hay ingredientes agregados/i)).toBeInTheDocument();

    const openModalBtn = screen.getByRole('button', { name: /agregar ingrediente/i });
    await user.click(openModalBtn);

    const addArrozBtn = screen.getByRole('button', { name: 'Add Arroz' });
    await user.click(addArrozBtn);

    expect(screen.getByText('Arroz Carnaroli')).toBeInTheDocument();
  });

  it('shows duplicate warning banner and updates existing ingredient instead of creating duplicate line', async () => {
    const user = userEvent.setup();
    render(
      <RecipeBOMStep
        dishId="d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"
        initialRecipe={sampleRecipe}
        onSaveRecipe={mockOnSaveRecipe}
        onRemoveIngredient={mockOnRemoveIngredient}
      />
    );

    const openModalBtn = screen.getByRole('button', { name: /agregar ingrediente/i });
    await user.click(openModalBtn);

    const addArrozBtn = screen.getByRole('button', { name: 'Add Arroz' });
    await user.click(addArrozBtn);

    expect(screen.getByText(/el ingrediente ya se encuentra en la receta/i)).toBeInTheDocument();
    // Verify there is still only 1 row for Arroz Carnaroli
    const arrozOccurrences = screen.getAllByText('Arroz Carnaroli');
    expect(arrozOccurrences.length).toBe(1);
  });

  it('validates strictly positive quantity and discrete unit integer precision', async () => {
    const user = userEvent.setup();
    render(
      <RecipeBOMStep
        dishId="d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"
        initialRecipe={sampleRecipe}
        onSaveRecipe={mockOnSaveRecipe}
        onRemoveIngredient={mockOnRemoveIngredient}
      />
    );

    const huevosQtyInput = screen.getByDisplayValue('2');
    await user.clear(huevosQtyInput);
    await user.type(huevosQtyInput, '2.5');

    const saveBtn = screen.getByRole('button', { name: /guardar receta/i });
    await user.click(saveBtn);

    expect(
      screen.getByText(/las unidades discretas.*enteros/i)
    ).toBeInTheDocument();
    expect(mockOnSaveRecipe).not.toHaveBeenCalled();
  });

  it('removes an ingredient line when clicking delete button', async () => {
    const user = userEvent.setup();
    render(
      <RecipeBOMStep
        dishId="d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"
        initialRecipe={sampleRecipe}
        onSaveRecipe={mockOnSaveRecipe}
        onRemoveIngredient={mockOnRemoveIngredient}
      />
    );

    const deleteButtons = screen.getAllByRole('button', { name: /eliminar/i });
    await user.click(deleteButtons[0]);

    expect(mockOnRemoveIngredient).toHaveBeenCalledWith('c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01');
    expect(screen.queryByText('Arroz Carnaroli')).not.toBeInTheDocument();
  });
});
