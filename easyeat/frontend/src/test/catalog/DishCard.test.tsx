import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DishCard } from '@/components/catalog/DishCard';
import type { DishCatalogItem } from '@/types/dinerCatalog';

const mockAvailableDish: DishCatalogItem = {
  id: 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55',
  name: 'Milanesa Napolitana con Fritas',
  description: 'Carne con salsa, jamón y queso con guarnición de papas.',
  category: 'Platos principales',
  price: 5500.0,
  photoUrl: '/api/v1/dishes/e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55/photo',
  isAvailable: true,
  isCompliant: true,
  conflictingRestrictions: [],
  recipeIngredients: ['Carne de ternera', 'Mozzarella', 'Jamón cocido', 'Papas'],
};

const mockOutOfStockDish: DishCatalogItem = {
  id: 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a57',
  name: 'Bife de Chorizo Clásico',
  description: 'Corte de 400g a la parrilla con guarnición.',
  category: 'Platos principales',
  price: 8900.0,
  photoUrl: null,
  isAvailable: false,
  isCompliant: true,
  conflictingRestrictions: [],
  recipeIngredients: ['Bife de chorizo', 'Sal parrillera'],
};

describe('DishCard Component Tests (T026, US2, COM-4.1.1, FR-007, FR-011, FR-012, FR-014)', () => {
  it('renders dish name, formatted price, description and photo when available', () => {
    const onSelectDish = vi.fn();
    render(<DishCard dish={mockAvailableDish} onSelectDish={onSelectDish} />);

    expect(screen.getByText('Milanesa Napolitana con Fritas')).toBeInTheDocument();
    expect(screen.getByText(/5\.500|5,500/)).toBeInTheDocument();
    expect(screen.getByText('Carne con salsa, jamón y queso con guarnición de papas.')).toBeInTheDocument();

    const img = screen.getByRole('img');
    expect(img).toHaveAttribute('src', mockAvailableDish.photoUrl);
  });

  it('renders placeholder image fallback when photoUrl is null', () => {
    render(<DishCard dish={mockOutOfStockDish} onSelectDish={vi.fn()} />);

    // When photoUrl is null, an SVG or placeholder with role/alt is rendered
    const placeholder = screen.getByTestId(`dish-placeholder-${mockOutOfStockDish.id}`);
    expect(placeholder).toBeInTheDocument();
  });

  it('falls back to placeholder image when image fails to load (onError)', () => {
    render(<DishCard dish={mockAvailableDish} onSelectDish={vi.fn()} />);

    const img = screen.getByRole('img');
    fireEvent.error(img);

    const placeholder = screen.getByTestId(`dish-placeholder-${mockAvailableDish.id}`);
    expect(placeholder).toBeInTheDocument();
  });

  it('renders out-of-stock badge and greyscale styling when dish is unavailable', () => {
    render(<DishCard dish={mockOutOfStockDish} onSelectDish={vi.fn()} />);

    expect(screen.getByText('Agotado')).toBeInTheDocument();
    const card = screen.getByTestId(`dish-card-${mockOutOfStockDish.id}`);
    expect(card.className).toMatch(/grayscale|opacity/);
  });

  it('renders in full color without Agotado badge when in read-only outside-hours mode', () => {
    render(<DishCard dish={mockAvailableDish} isReadOnly={true} onSelectDish={vi.fn()} />);

    expect(screen.queryByText('Agotado')).not.toBeInTheDocument();
    const card = screen.getByTestId(`dish-card-${mockAvailableDish.id}`);
    expect(card.className).not.toMatch(/grayscale/);
  });

  it('triggers onSelectDish when card is clicked', () => {
    const onSelectDish = vi.fn();
    render(<DishCard dish={mockAvailableDish} onSelectDish={onSelectDish} />);

    const card = screen.getByTestId(`dish-card-${mockAvailableDish.id}`);
    fireEvent.click(card);

    expect(onSelectDish).toHaveBeenCalledTimes(1);
    expect(onSelectDish).toHaveBeenCalledWith(mockAvailableDish);
  });
});
