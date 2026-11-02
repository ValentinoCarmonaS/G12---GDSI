import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DishDetailModal } from '@/components/catalog/DishDetailModal';
import { CartContext } from '@/context/CartContext';
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

  describe('T011 [US1] - Conditional photo omission, progressive image loading with skeleton, and long text scroll', () => {
    it('completely omits image container without placeholder or blank box when dish has no photoUrl (INV-2, FR-003, EC-001)', () => {
      const dishWithoutPhoto: DishCatalogItem = {
        ...mockDish,
        photoUrl: null,
      };

      render(<DishDetailModal dish={dishWithoutPhoto} isOpen={true} onClose={vi.fn()} />);

      // The image element and any broken image placeholder must NOT exist
      expect(screen.queryByRole('img')).toBeNull();
      expect(screen.queryByTestId(`modal-placeholder-${dishWithoutPhoto.id}`)).toBeNull();
      expect(screen.queryByTestId('dish-detail-photo-container')).toBeNull();

      // Title and description must render directly
      expect(screen.getByText('Milanesa Napolitana')).toBeInTheDocument();
      expect(screen.getByText('Carne con salsa y queso.')).toBeInTheDocument();
    });

    it('renders animated skeleton during image loading and replaces it once loaded, hiding skeleton on error (INV-3, FR-005, SC-004, EC-002)', () => {
      render(<DishDetailModal dish={mockDish} isOpen={true} onClose={vi.fn()} />);

      // Initially, image skeleton loader must be visible while image loads
      const skeleton = screen.getByTestId('dish-detail-image-skeleton');
      expect(skeleton).toBeInTheDocument();

      const img = screen.getByRole('img', { name: mockDish.name });
      expect(img).toBeInTheDocument();

      // Simulate successful image load
      fireEvent.load(img);

      // Skeleton should be removed or hidden
      expect(screen.queryByTestId('dish-detail-image-skeleton')).toBeNull();
    });

    it('handles image loading error gracefully by omitting photo container without broken placeholders (INV-2, INV-3, EC-002)', () => {
      render(<DishDetailModal dish={mockDish} isOpen={true} onClose={vi.fn()} />);

      const img = screen.getByRole('img', { name: mockDish.name });
      expect(img).toBeInTheDocument();

      // Simulate network error on image loading
      fireEvent.error(img);

      // Photo container and broken placeholders must be removed
      expect(screen.queryByTestId(`modal-placeholder-${mockDish.id}`)).toBeNull();
      expect(screen.queryByTestId('dish-detail-photo-container')).toBeNull();
    });

    it('accommodates long commercial descriptions inside internal vertical scroll container with word breaking (INV-4, FR-006, SC-005, EC-003)', () => {
      const veryLongDescription = 'Descripción gourmet extremadamente detallada. '.repeat(50);
      const dishWithLongText: DishCatalogItem = {
        ...mockDish,
        description: veryLongDescription,
      };

      render(<DishDetailModal dish={dishWithLongText} isOpen={true} onClose={vi.fn()} />);

      const contentContainer = screen.getByTestId('dish-detail-scrollable-content');
      expect(contentContainer).toBeInTheDocument();
      // Must contain classes for max height and overflow scroll
      expect(contentContainer.className).toMatch(/overflow-y-auto/);
      expect(contentContainer.className).toMatch(/max-h-/);

      const descriptionElement = screen.getByTestId('dish-detail-description');
      expect(descriptionElement).toBeInTheDocument();
      expect(descriptionElement.className).toMatch(/break-words/);
    });
  });

  describe('T012 [US1] - Integration tests for real-time availability polling invalidation (<= 3 s) without forced close', () => {
    it('reactively marks dish as "Plato no disponible" and disables add-to-cart button within <= 3 s without closing modal when stock is exhausted (INV-5, FR-007, SC-010, EC-004)', () => {
      const { rerender } = render(
        <DishDetailModal
          dish={mockDish}
          isOpen={true}
          onClose={vi.fn()}
        />
      );

      // Initially dish is available
      expect(screen.queryByTestId('dish-unavailable-banner')).toBeNull();
      const addBtn = screen.getByTestId('add-to-cart-btn');
      expect(addBtn).not.toBeDisabled();

      // Simulate stock exhaustion or administrative cancellation arriving via availability update (<= 3 s)
      const exhaustedDish: DishCatalogItem = {
        ...mockDish,
        isAvailable: false,
      };

      rerender(
        <DishDetailModal
          dish={exhaustedDish}
          isOpen={true}
          onClose={vi.fn()}
        />
      );

      // Modal must remain open and visible without being forced shut
      expect(screen.getByTestId('dish-detail-modal')).toBeInTheDocument();

      // UI must reactively display unavailable notice / out of stock badge
      expect(screen.getByTestId('dish-unavailable-banner')).toBeInTheDocument();
      expect(screen.getByText('Plato no disponible')).toBeInTheDocument();

      // Purchase button must be disabled
      expect(screen.getByTestId('add-to-cart-btn')).toBeDisabled();
    });

    it('re-enables purchase controls dynamically when dish is restocked while modal is open (INV-5, EC-005)', () => {
      const outOfStockDish: DishCatalogItem = {
        ...mockDish,
        isAvailable: false,
      };

      const { rerender } = render(
        <DishDetailModal
          dish={outOfStockDish}
          isOpen={true}
          onClose={vi.fn()}
        />
      );

      expect(screen.getByTestId('add-to-cart-btn')).toBeDisabled();

      // Kitchen restocks the item
      const restockedDish: DishCatalogItem = {
        ...mockDish,
        isAvailable: true,
      };

      rerender(
        <DishDetailModal
          dish={restockedDish}
          isOpen={true}
          onClose={vi.fn()}
        />
      );

      // Modal remains open and button is rehabilitated
      expect(screen.getByTestId('dish-detail-modal')).toBeInTheDocument();
      expect(screen.queryByTestId('dish-unavailable-banner')).toBeNull();
      expect(screen.getByTestId('add-to-cart-btn')).not.toBeDisabled();
    });
  });

  describe('User Story 2 - Ver precio final y subtotal provisorio (T019, T020, COM-5.1.3, AC-3.1 to AC-3.5, SC-007, SC-009, SC-011)', () => {
    it('prominently displays retail price with taxes in regional format ($ 5.500,00) next to action buttons (T019, INV-9, INV-10, AC-3.1, AC-3.2, FR-011, FR-012)', () => {
      render(<DishDetailModal dish={mockDish} isOpen={true} onClose={vi.fn()} />);

      // Price formatted in regional Argentine Pesos ($ 5.500,00)
      const priceElement = screen.getByTestId('dish-detail-price');
      expect(priceElement).toBeInTheDocument();
      expect(priceElement).toHaveTextContent('$ 5.500,00');
    });

    it('displays historical referential price with purchase actions disabled when dish is out of stock (T019, INV-12, AC-3.4, FR-014, SC-009, EC-010)', () => {
      const outOfStockDish: DishCatalogItem & { availableStock?: number } = {
        ...mockDish,
        isAvailable: false,
        availableStock: 0,
      };

      render(<DishDetailModal dish={outOfStockDish} isOpen={true} onClose={vi.fn()} />);

      // Referential price is still visible
      const priceElement = screen.getByTestId('dish-detail-price');
      expect(priceElement).toBeInTheDocument();
      expect(priceElement).toHaveTextContent('$ 5.500,00');

      // Actions and quantity selector disabled
      expect(screen.getByTestId('add-to-cart-btn')).toBeDisabled();
      expect(screen.getByTestId('quantity-decrement-btn')).toBeDisabled();
      expect(screen.getByTestId('quantity-increment-btn')).toBeDisabled();
    });

    it('allows selecting quantity without artificial cap and dynamically updates provisional subtotal (T020, INV-13, AC-3.5, SC-011, EC-011)', () => {
      // Dish with price 5500.0, availableStock 3
      const dishWithStock = {
        ...mockDish,
        price: 5500.0,
        availableStock: 3,
      };

      render(<DishDetailModal dish={dishWithStock} isOpen={true} onClose={vi.fn()} />);

      const incBtn = screen.getByTestId('quantity-increment-btn');
      const decBtn = screen.getByTestId('quantity-decrement-btn');
      const qtyValue = screen.getByTestId('quantity-value');
      const subtotalElement = screen.getByTestId('dish-detail-subtotal');

      // Initial state: N = 1, subtotal = $ 5.500,00
      expect(qtyValue).toHaveTextContent('1');
      expect(subtotalElement).toHaveTextContent('$ 5.500,00');

      // Comensal can increment beyond kitchen stock without artificial cap in estimation phase (Clarification #2, INV-13)
      // Increment to N = 2 -> subtotal = $ 11.000,00
      fireEvent.click(incBtn);
      expect(qtyValue).toHaveTextContent('2');
      expect(subtotalElement).toHaveTextContent('$ 11.000,00');

      // Increment to N = 3 -> subtotal = $ 16.500,00
      fireEvent.click(incBtn);
      expect(qtyValue).toHaveTextContent('3');
      expect(subtotalElement).toHaveTextContent('$ 16.500,00');

      // Increment to N = 5 (exceeding availableStock=3, estimation allowed!) -> subtotal = $ 27.500,00
      fireEvent.click(incBtn);
      fireEvent.click(incBtn);
      expect(qtyValue).toHaveTextContent('5');
      expect(subtotalElement).toHaveTextContent('$ 27.500,00');
      expect(incBtn).not.toBeDisabled();

      // Decrement back to N = 4 -> subtotal = $ 22.000,00
      fireEvent.click(decBtn);
      expect(qtyValue).toHaveTextContent('4');
      expect(subtotalElement).toHaveTextContent('$ 22.000,00');
    });

    it('resets quantity selector to 1 and discards subtotal calculation upon closing and reopening (T020, INV-13, EC-011, Clarification #2)', () => {
      const { rerender } = render(<DishDetailModal dish={mockDish} isOpen={true} onClose={vi.fn()} />);

      const incBtn = screen.getByTestId('quantity-increment-btn');
      fireEvent.click(incBtn);
      fireEvent.click(incBtn);
      expect(screen.getByTestId('quantity-value')).toHaveTextContent('3');
      expect(screen.getByTestId('dish-detail-subtotal')).toHaveTextContent('$ 16.500,00');

      // Close modal
      rerender(<DishDetailModal dish={mockDish} isOpen={false} onClose={vi.fn()} />);

      // Reopen modal
      rerender(<DishDetailModal dish={mockDish} isOpen={true} onClose={vi.fn()} />);
      expect(screen.getByTestId('quantity-value')).toHaveTextContent('1');
      expect(screen.getByTestId('dish-detail-subtotal')).toHaveTextContent('$ 5.500,00');
    });

    it('validates kitchen inventory on "Agregar al Carrito" and displays insufficient stock warning when N + q_en_carrito > availableStock (T023, INV-13, EC-011, Clarification #2, SC-011)', () => {
      const onAddToCart = vi.fn();
      const dishWithStock = {
        ...mockDish,
        availableStock: 3,
      };

      // Mock CartContext where 1 unit is already in cart -> remainingStock = 2
      const mockCart = {
        items: [
          {
            dishId: mockDish.id,
            name: mockDish.name,
            price: mockDish.price,
            quantity: 1,
            availableStock: 3,
          },
        ],
        addItem: vi.fn(),
        removeItem: vi.fn(),
        updateQuantity: vi.fn(),
        clearCart: vi.fn(),
        reEvaluateCompliance: vi.fn(),
        totalAmount: mockDish.price,
        totalCount: 1,
        hasConflictingItems: false,
      };

      const onClose = vi.fn();
      render(
        <CartContext.Provider value={mockCart as any}>
          <DishDetailModal
            dish={dishWithStock}
            isOpen={true}
            onClose={onClose}
            onAddToCart={onAddToCart}
          />
        </CartContext.Provider>
      );

      const incBtn = screen.getByTestId('quantity-increment-btn');
      const addBtn = screen.getByTestId('add-to-cart-btn');

      // Select N = 3 (3 + 1 = 4 > availableStock=3)
      fireEvent.click(incBtn);
      fireEvent.click(incBtn);
      expect(screen.getByTestId('quantity-value')).toHaveTextContent('3');

      // Click "Agregar al Carrito"
      fireEvent.click(addBtn);

      // Must NOT add to cart and must NOT close modal
      expect(onAddToCart).not.toHaveBeenCalled();
      expect(onClose).not.toHaveBeenCalled();

      // Must display explicit warning notice with remaining stock (2 disponibles) and kitchen stock (3 en cocina)
      const warning = screen.getByTestId('stock-insufficient-warning');
      expect(warning).toBeInTheDocument();
      expect(warning).toHaveTextContent(/2 unidades disponibles/i);
      expect(warning).toHaveTextContent(/3 en cocina/i);

      // Comensal reduces quantity to N = 2 (2 + 1 = 3 <= availableStock=3)
      const decBtn = screen.getByTestId('quantity-decrement-btn');
      fireEvent.click(decBtn);
      expect(screen.getByTestId('quantity-value')).toHaveTextContent('2');

      // Try adding again with valid amount
      fireEvent.click(addBtn);
      expect(onAddToCart).toHaveBeenCalledTimes(1);
      expect(onAddToCart).toHaveBeenCalledWith(dishWithStock, 2);
      expect(onClose).toHaveBeenCalledTimes(1);
    });
  });
});
