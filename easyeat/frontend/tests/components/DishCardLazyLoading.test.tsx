import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { DishCard } from '@/components/catalog/DishCard';
import type { DishCatalogItem } from '@/types/dinerCatalog';

describe('DishCard lazy loading & async decoding (T021, FR-009)', () => {
  const sampleDish: DishCatalogItem = {
    id: 'dish-lazy-1',
    name: 'Milanesa a la Napolitana',
    description: 'Clásica con papas fritas',
    category: 'Platos principales',
    price: 9500,
    photoUrl: '/api/v1/dishes/dish-lazy-1/photo',
    isAvailable: true,
    availableStock: 5,
    isCompliant: true,
    conflictingRestrictions: [],
    recipeIngredients: ['Carne', 'Tomate', 'Muzzarella'],
    allergens: [],
    lifestyles: [],
  };

  it('renders dish image with loading="lazy" and decoding="async"', () => {
    render(<DishCard dish={sampleDish} />);

    const img = screen.getByRole('img', { name: sampleDish.name });
    expect(img).toHaveAttribute('loading', 'lazy');
    expect(img).toHaveAttribute('decoding', 'async');
  });
});
