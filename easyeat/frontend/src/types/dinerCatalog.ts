/**
 * Diner Catalog and Multi-Tenant Establishment Shared Types
 * Feature: 008-diner-catalog-multi-tenant
 */

export const ESTABLISHMENT_STATUSES = ['ACTIVE', 'PAUSED', 'DISABLED'] as const;
export type EstablishmentStatus = (typeof ESTABLISHMENT_STATUSES)[number];

export const CLOSED_GASTRONOMIC_CATEGORIES = [
  'Entradas',
  'Platos principales',
  'Postres',
  'Bebidas',
  'Ensaladas',
] as const;
export type GastronomicCategory = (typeof CLOSED_GASTRONOMIC_CATEGORIES)[number];

export function isEstablishmentStatus(status: unknown): status is EstablishmentStatus {
  return typeof status === 'string' && ESTABLISHMENT_STATUSES.includes(status as EstablishmentStatus);
}

export function isEstablishmentActive(establishment: { status: EstablishmentStatus }): boolean {
  return establishment.status === 'ACTIVE';
}

export interface EstablishmentSummary {
  id: string;
  name: string;
  status: EstablishmentStatus;
  openingTime: string;
  closingTime: string;
  timezone: string;
  isOpen: boolean;
}

export interface EstablishmentDetail {
  id: string;
  name: string;
  status: EstablishmentStatus;
  isOpen: boolean;
  nextOpeningSchedule?: string | null;
}

export interface DishCatalogItem {
  id: string;
  name: string;
  description: string | null;
  category: string;
  price: number;
  photoUrl: string | null;
  isAvailable: boolean;
  isCompliant: boolean;
  conflictingRestrictions: string[];
  recipeIngredients: string[];
}

export interface CategoryGroup {
  category: string;
  dishes: DishCatalogItem[];
}

export interface CatalogResponse {
  establishment: EstablishmentDetail;
  categories: CategoryGroup[];
}

export interface AvailabilityMap {
  availability: Record<string, boolean>;
}
