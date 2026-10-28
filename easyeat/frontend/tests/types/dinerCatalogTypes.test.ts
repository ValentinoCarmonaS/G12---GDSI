import { describe, it, expect } from 'vitest';
import {
  ESTABLISHMENT_STATUSES,
  CLOSED_GASTRONOMIC_CATEGORIES,
  isEstablishmentStatus,
  isEstablishmentActive,
} from '@/types/dinerCatalog';
import type {
  EstablishmentStatus,
  EstablishmentSummary,
  EstablishmentDetail,
  DishCatalogItem,
  CategoryGroup,
  CatalogResponse,
  AvailabilityMap,
} from '@/types/dinerCatalog';

describe('Diner Catalog Types and Type Guards (T005)', () => {
  it('defines valid establishment statuses and closed gastronomic categories', () => {
    expect(ESTABLISHMENT_STATUSES).toEqual(['ACTIVE', 'PAUSED', 'DISABLED']);
    expect(CLOSED_GASTRONOMIC_CATEGORIES).toEqual([
      'Entradas',
      'Platos principales',
      'Postres',
      'Bebidas',
      'Ensaladas',
    ]);
  });

  it('validates isEstablishmentStatus type guard correctly', () => {
    expect(isEstablishmentStatus('ACTIVE')).toBe(true);
    expect(isEstablishmentStatus('PAUSED')).toBe(true);
    expect(isEstablishmentStatus('DISABLED')).toBe(true);
    expect(isEstablishmentStatus('DELETED')).toBe(false);
    expect(isEstablishmentStatus(null)).toBe(false);
    expect(isEstablishmentStatus(undefined)).toBe(false);
  });

  it('validates isEstablishmentActive predicate', () => {
    expect(isEstablishmentActive({ status: 'ACTIVE' })).toBe(true);
    expect(isEstablishmentActive({ status: 'PAUSED' })).toBe(false);
    expect(isEstablishmentActive({ status: 'DISABLED' })).toBe(false);
  });

  it('verifies type contract for EstablishmentSummary and EstablishmentDetail', () => {
    const summary: EstablishmentSummary = {
      id: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      name: 'La Farola de Palermo',
      status: 'ACTIVE',
      openingTime: '11:00:00',
      closingTime: '23:00:00',
      timezone: 'America/Argentina/Buenos_Aires',
      isOpen: true,
    };

    expect(summary.id).toBe('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11');
    expect(summary.isOpen).toBe(true);

    const detail: EstablishmentDetail = {
      id: summary.id,
      name: summary.name,
      status: 'ACTIVE',
      isOpen: false,
      nextOpeningSchedule: 'Abre hoy a las 11:00',
    };

    expect(detail.isOpen).toBe(false);
    expect(detail.nextOpeningSchedule).toBe('Abre hoy a las 11:00');
  });

  it('verifies type contract for DishCatalogItem, CategoryGroup, and CatalogResponse', () => {
    const dish: DishCatalogItem = {
      id: 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55',
      name: 'Milanesa Napolitana con Papas Fritas',
      description: 'Milanesa con salsa casera, jamón, mozzarella y papas',
      category: 'Platos principales',
      price: 5500.0,
      photoUrl: '/api/v1/dishes/e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55/photo',
      isAvailable: true,
      isCompliant: true,
      conflictingRestrictions: [],
      recipeIngredients: ['Carne de ternera', 'Mozzarella', 'Jamón cocido', 'Papas'],
    };

    const group: CategoryGroup = {
      category: 'Platos principales',
      dishes: [dish],
    };

    const response: CatalogResponse = {
      establishment: {
        id: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        name: 'La Farola de Palermo',
        status: 'ACTIVE',
        isOpen: true,
      },
      categories: [group],
    };

    expect(response.categories[0].dishes[0].price).toBe(5500.0);
    expect(response.categories[0].dishes[0].isAvailable).toBe(true);
  });

  it('verifies type contract for AvailabilityMap', () => {
    const map: AvailabilityMap = {
      availability: {
        'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55': true,
        'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01': false,
      },
    };

    expect(map.availability['e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55']).toBe(true);
    expect(map.availability['d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01']).toBe(false);
  });
});
