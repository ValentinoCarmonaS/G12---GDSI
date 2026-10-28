import { vi } from 'vitest';
import type {
  EstablishmentSummary,
  EstablishmentDetail,
  DishCatalogItem,
  CategoryGroup,
  CatalogResponse,
  AvailabilityMap,
} from '@/types/dinerCatalog';

export const MOCK_TENANT_ID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';
export const MOCK_TENANT_ID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22';
export const MOCK_TENANT_PAUSED_ID = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33';
export const MOCK_TENANT_DISABLED_ID = 'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44';

export const MOCK_DISH_ID_1 = 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55';
export const MOCK_DISH_ID_2 = 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a56';
export const MOCK_DISH_ID_OUT_OF_STOCK = 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a57';
export const MOCK_DISH_ID_DESSERT = 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a58';

export const mockEstablishmentSummaryActive: EstablishmentSummary = {
  id: MOCK_TENANT_ID,
  name: 'La Farola de Palermo',
  status: 'ACTIVE',
  openingTime: '11:00:00',
  closingTime: '23:00:00',
  timezone: 'America/Argentina/Buenos_Aires',
  isOpen: true,
};

export const mockEstablishmentSummaryActive2: EstablishmentSummary = {
  id: MOCK_TENANT_ID_2,
  name: 'Parrilla Don Julio',
  status: 'ACTIVE',
  openingTime: '12:00:00',
  closingTime: '00:00:00',
  timezone: 'America/Argentina/Buenos_Aires',
  isOpen: true,
};

export const mockEstablishmentSummaryPaused: EstablishmentSummary = {
  id: MOCK_TENANT_PAUSED_ID,
  name: 'Café Martinez Recoleta',
  status: 'PAUSED',
  openingTime: '08:00:00',
  closingTime: '20:00:00',
  timezone: 'America/Argentina/Buenos_Aires',
  isOpen: false,
};

export const mockEstablishmentSummaryDisabled: EstablishmentSummary = {
  id: MOCK_TENANT_DISABLED_ID,
  name: 'Pizzería Güerrín Central',
  status: 'DISABLED',
  openingTime: '12:00:00',
  closingTime: '01:00:00',
  timezone: 'America/Argentina/Buenos_Aires',
  isOpen: false,
};

export const mockEstablishmentSummaries: EstablishmentSummary[] = [
  mockEstablishmentSummaryActive,
  mockEstablishmentSummaryActive2,
  mockEstablishmentSummaryPaused,
  mockEstablishmentSummaryDisabled,
];

export const mockEstablishmentDetailActive: EstablishmentDetail = {
  id: MOCK_TENANT_ID,
  name: 'La Farola de Palermo',
  status: 'ACTIVE',
  isOpen: true,
  nextOpeningSchedule: null,
};

export const mockDishAvailableItem: DishCatalogItem = {
  id: MOCK_DISH_ID_1,
  name: 'Milanesa Napolitana con Papas Fritas',
  description: 'Milanesa de ternera con salsa de tomate casera, jamón cocido, queso mozzarella y orégano.',
  category: 'Platos principales',
  price: 5500.0,
  photoUrl: `/api/v1/dishes/${MOCK_DISH_ID_1}/photo`,
  isAvailable: true,
  isCompliant: true,
  conflictingRestrictions: [],
  recipeIngredients: ['Carne de ternera', 'Mozzarella', 'Jamón cocido', 'Papas'],
};

export const mockDishOutOfStockItem: DishCatalogItem = {
  id: MOCK_DISH_ID_OUT_OF_STOCK,
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

export const mockDishDessertItem: DishCatalogItem = {
  id: MOCK_DISH_ID_DESSERT,
  name: 'Flan Casero con Dulce de Leche',
  description: 'Tradicional flan de huevos con dulce de leche colonial.',
  category: 'Postres',
  price: 2500.0,
  photoUrl: `/api/v1/dishes/${MOCK_DISH_ID_DESSERT}/photo`,
  isAvailable: true,
  isCompliant: true,
  conflictingRestrictions: [],
  recipeIngredients: ['Leche', 'Huevos', 'Azúcar', 'Dulce de leche'],
};

export const mockCategorizedGroups: CategoryGroup[] = [
  {
    category: 'Platos principales',
    dishes: [mockDishAvailableItem, mockDishOutOfStockItem],
  },
  {
    category: 'Postres',
    dishes: [mockDishDessertItem],
  },
];

export const mockDinerCatalogResponse: CatalogResponse = {
  establishment: mockEstablishmentDetailActive,
  categories: mockCategorizedGroups,
};

export const mockAvailabilityMap: AvailabilityMap = {
  availability: {
    [MOCK_DISH_ID_1]: true,
    [MOCK_DISH_ID_2]: true,
    [MOCK_DISH_ID_OUT_OF_STOCK]: false,
    [MOCK_DISH_ID_DESSERT]: true,
  },
};

export function createMockEstablishmentSummary(
  overrides?: Partial<EstablishmentSummary>
): EstablishmentSummary {
  return {
    ...mockEstablishmentSummaryActive,
    ...overrides,
  };
}

export function createMockDishCatalogItem(
  overrides?: Partial<DishCatalogItem>
): DishCatalogItem {
  return {
    ...mockDishAvailableItem,
    id: `dish-${Date.now()}`,
    ...overrides,
  };
}

export function createMockCatalogResponse(
  overrides?: Partial<CatalogResponse>
): CatalogResponse {
  return {
    ...mockDinerCatalogResponse,
    ...overrides,
  };
}

export function createMockAvailabilityMap(
  overrides?: Record<string, boolean>
): AvailabilityMap {
  return {
    availability: {
      ...mockAvailabilityMap.availability,
      ...overrides,
    },
  };
}

export interface DinerCatalogApiMockHandlers {
  searchEstablishments: ReturnType<typeof vi.fn>;
  getEstablishmentCatalog: ReturnType<typeof vi.fn>;
  getCatalogAvailability: ReturnType<typeof vi.fn>;
}

export function setupDinerCatalogApiMockHandlers(
  overrides?: Partial<DinerCatalogApiMockHandlers>
): DinerCatalogApiMockHandlers {
  const defaultHandlers: DinerCatalogApiMockHandlers = {
    searchEstablishments: vi.fn().mockImplementation((query: string) => {
      if (!query || query.trim().length < 3) {
        return Promise.resolve([]);
      }
      const term = query.trim().toLowerCase();
      return Promise.resolve(
        mockEstablishmentSummaries.filter(
          (e) => e.name.toLowerCase().includes(term) && e.status === 'ACTIVE'
        )
      );
    }),
    getEstablishmentCatalog: vi.fn().mockResolvedValue(mockDinerCatalogResponse),
    getCatalogAvailability: vi.fn().mockResolvedValue(mockAvailabilityMap),
  };

  return {
    ...defaultHandlers,
    ...overrides,
  };
}
