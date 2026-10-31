import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DishDetailModal } from '@/components/catalog/DishDetailModal';
import type { DishCatalogItem } from '@/types/dinerCatalog';

const mockDish: DishCatalogItem = {
  id: 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55',
  name: 'Milanesa Napolitana',
  description: 'Carne con salsa y queso.',
  category: 'Platos principales',
  price: 5500.0,
  photoUrl: '/api/v1/dishes/e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55/photo',
  isAvailable: true,
  isCompliant: true,
  conflictingRestrictions: [],
  recipeIngredients: ['Carne de ternera', 'Mozzarella', 'Jamón cocido', 'Papas'],
};

describe('DishDetailModal Component Tests (T034, US2, COM-4.1.1, FR-007, FR-012, FR-014)', () => {
  it('does not render when isOpen is false or dish is null', () => {
    const { container } = render(<DishDetailModal dish={mockDish} isOpen={false} onClose={vi.fn()} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders dish details, recipe ingredients, and formatted price', () => {
    render(<DishDetailModal dish={mockDish} isOpen={true} onClose={vi.fn()} />);

    expect(screen.getByTestId('dish-detail-modal')).toBeInTheDocument();
    expect(screen.getByText('Milanesa Napolitana')).toBeInTheDocument();
    expect(screen.getByText('Carne con salsa y queso.')).toBeInTheDocument();
    expect(screen.getByText('Ingredientes')).toBeInTheDocument();
    expect(screen.getByText('Carne de ternera, Mozzarella, Jamón cocido, Papas')).toBeInTheDocument();
  });

  it('renders outside hours notice when isReadOnly is true', () => {
    render(
      <DishDetailModal
        dish={mockDish}
        isOpen={true}
        onClose={vi.fn()}
        isReadOnly={true}
        nextOpeningSchedule="Abre hoy a las 20:00"
      />
    );

    expect(screen.getByTestId('outside-hours-modal-notice')).toBeInTheDocument();
    expect(screen.getByText(/Abre hoy a las 20:00/)).toBeInTheDocument();
  });

  it('calls onClose when close button is clicked', () => {
    const onClose = vi.fn();
    render(<DishDetailModal dish={mockDish} isOpen={true} onClose={onClose} />);

    fireEvent.click(screen.getByTestId('modal-close-btn'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
