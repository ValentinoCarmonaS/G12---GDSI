import { describe, it, expect } from 'vitest';
import { formatDishItem } from '../../utils/kdsFormatters';

describe('T021: formatDishItem in kdsFormatters (US3 - CHF-3.2.1)', () => {
  it('debe formatear un ítem simple bajo el formato unificado "[Cantidad]x [Nombre]"', () => {
    expect(formatDishItem(1, 'Hamburguesa Clásica')).toBe('1x Hamburguesa Clásica');
  });

  it('debe manejar cantidades múltiples correctamente', () => {
    expect(formatDishItem(3, 'Papas Fritas')).toBe('3x Papas Fritas');
    expect(formatDishItem(5, 'Ensalada César')).toBe('5x Ensalada César');
  });

  it('debe limpiar espacios en blanco redundantes del nombre del plato', () => {
    expect(formatDishItem(2, '  Tarta de Verduras  ')).toBe('2x Tarta de Verduras');
  });

  it('debe tolerar nombres vacíos o nulos sin lanzar excepción no controlada', () => {
    expect(formatDishItem(1, '')).toBe('1x');
    // @ts-expect-error testing null safety
    expect(formatDishItem(4, null)).toBe('4x');
  });
});
