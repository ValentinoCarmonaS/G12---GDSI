import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import {
  CatalogSearchFilter,
  matchesDishQuery,
  normalizeSearchTerm,
} from '@/components/catalog/CatalogSearchFilter';

describe('CatalogSearchFilter Component & Filtering Logic (T045, US4, COM-4.2.3)', () => {
  describe('Normalization and Matching Unit Logic', () => {
    it('normalizes diacritics and converts to lower case', () => {
      expect(normalizeSearchTerm('Café')).toBe('cafe');
      expect(normalizeSearchTerm('JAMÓN')).toBe('jamon');
      expect(normalizeSearchTerm('  Ñandú  ')).toBe('nandu');
    });

    it('matches dishes by partial name, case- and diacritic-insensitive', () => {
      const dish = {
        name: 'Jamón Serrano con Rúcula',
        recipeIngredients: ['Jamón', 'Rúcula', 'Aceite de oliva'],
      };

      // Diacritic-insensitive
      expect(matchesDishQuery(dish, 'jamon')).toBe(true);
      // Case-insensitive
      expect(matchesDishQuery(dish, 'SERRANO')).toBe(true);
      // Substring
      expect(matchesDishQuery(dish, 'rucu')).toBe(true);
    });

    it('matches dishes by recipe ingredients', () => {
      const dish = {
        name: 'Hamburguesa Doble',
        recipeIngredients: ['Carne vacuna', 'Queso Cheddar', 'Panceta'],
      };

      // Matches ingredient without accent or case
      expect(matchesDishQuery(dish, 'cheddar')).toBe(true);
      expect(matchesDishQuery(dish, 'QUESO')).toBe(true);
      expect(matchesDishQuery(dish, 'panceta')).toBe(true);
    });

    it('returns false when neither name nor ingredients match', () => {
      const dish = {
        name: 'Ensalada César',
        recipeIngredients: ['Lechuga', 'Pollo', 'Parmesano', 'Croutons'],
      };

      expect(matchesDishQuery(dish, 'chocolate')).toBe(false);
      expect(matchesDishQuery(dish, 'pescado')).toBe(false);
    });

    it('preserves matching all dishes when query is empty or whitespace only', () => {
      const dish = {
        name: 'Flan Casero',
        recipeIngredients: ['Huevos', 'Leche'],
      };

      expect(matchesDishQuery(dish, '')).toBe(true);
      expect(matchesDishQuery(dish, '   ')).toBe(true);
    });
  });

  describe('Component Rendering & User Interactions', () => {
    it('renders input with placeholder and role', () => {
      render(
        <CatalogSearchFilter
          value=""
          onChange={vi.fn()}
          onClear={vi.fn()}
          placeholder="Buscar por plato o ingrediente..."
        />
      );

      const input = screen.getByPlaceholderText('Buscar por plato o ingrediente...');
      expect(input).toBeInTheDocument();
      expect(input).toHaveValue('');
    });

    it('invokes onChange when user types into search input', () => {
      const handleChange = vi.fn();
      render(
        <CatalogSearchFilter
          value=""
          onChange={handleChange}
          onClear={vi.fn()}
        />
      );

      const input = screen.getByRole('textbox', { name: /buscar en el catálogo/i });
      fireEvent.change(input, { target: { value: 'pasta' } });

      expect(handleChange).toHaveBeenCalledWith('pasta');
    });

    it('displays clear button when input has value and triggers onClear on click', () => {
      const handleClear = vi.fn();
      render(
        <CatalogSearchFilter
          value="hamburguesa"
          onChange={vi.fn()}
          onClear={handleClear}
        />
      );

      const clearButton = screen.getByRole('button', { name: /limpiar búsqueda/i });
      expect(clearButton).toBeInTheDocument();

      fireEvent.click(clearButton);
      expect(handleClear).toHaveBeenCalledTimes(1);
    });

    it('does not display clear button when value is empty', () => {
      render(
        <CatalogSearchFilter
          value=""
          onChange={vi.fn()}
          onClear={vi.fn()}
        />
      );

      expect(screen.queryByRole('button', { name: /limpiar búsqueda/i })).not.toBeInTheDocument();
    });
  });
});
