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

  describe('T010 [US1] - Modal opening and scroll coordinate capture/restoration on close', () => {
    it('captures window.scrollY upon opening and restores it with instant behavior upon close via button', () => {
      const originalScrollTo = window.scrollTo;
      const scrollToMock = vi.fn();
      window.scrollTo = scrollToMock;

      // Simulate user scrolled down 1200px in the catalog grid (INV-1, SC-002)
      Object.defineProperty(window, 'scrollY', { value: 1200, writable: true });

      const onClose = vi.fn();
      const { rerender } = render(
        <DishDetailModal dish={mockDish} isOpen={true} onClose={onClose} />
      );

      // Verify modal is open
      expect(screen.getByTestId('dish-detail-modal')).toBeInTheDocument();

      // Click close button
      fireEvent.click(screen.getByTestId('modal-close-btn'));
      expect(onClose).toHaveBeenCalledTimes(1);

      // Rerender with isOpen=false simulating parent closing the modal
      rerender(<DishDetailModal dish={mockDish} isOpen={false} onClose={onClose} />);

      // Verify scrollTo was called with exact vertical coordinate and instant behavior (INV-1, SC-002)
      expect(scrollToMock).toHaveBeenCalledWith({ top: 1200, behavior: 'instant' });

      window.scrollTo = originalScrollTo;
    });

    it('restores window.scrollY coordinate when modal is closed by clicking the backdrop outside', () => {
      const originalScrollTo = window.scrollTo;
      const scrollToMock = vi.fn();
      window.scrollTo = scrollToMock;

      Object.defineProperty(window, 'scrollY', { value: 850, writable: true });

      const onClose = vi.fn();
      const { rerender } = render(
        <DishDetailModal dish={mockDish} isOpen={true} onClose={onClose} />
      );

      // Click on backdrop outside modal container
      fireEvent.click(screen.getByTestId('dish-detail-modal'));
      expect(onClose).toHaveBeenCalledTimes(1);

      rerender(<DishDetailModal dish={mockDish} isOpen={false} onClose={onClose} />);

      expect(scrollToMock).toHaveBeenCalledWith({ top: 850, behavior: 'instant' });

      window.scrollTo = originalScrollTo;
    });
  });
});
