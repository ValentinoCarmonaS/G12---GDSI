import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  MOCK_TENANT_ID,
  MOCK_TENANT_ID_2,
  MOCK_TENANT_PAUSED_ID,
  MOCK_DISH_ID_1,
  MOCK_DISH_ID_OUT_OF_STOCK,
  mockEstablishmentSummaryActive,
  mockEstablishmentSummaryPaused,
  mockEstablishmentSummaries,
  mockDinerCatalogResponse,
  mockAvailabilityMap,
  createMockEstablishmentSummary,
  createMockDishCatalogItem,
  createMockCatalogResponse,
  createMockAvailabilityMap,
  setupDinerCatalogApiMockHandlers,
} from './dinerCatalogHandlers';

describe('dinerCatalogHandlers Mock Fixtures and Setup (T002)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('provides valid mock establishment summary fixtures', () => {
    expect(mockEstablishmentSummaryActive.id).toBe(MOCK_TENANT_ID);
    expect(mockEstablishmentSummaryActive.name).toBe('La Farola de Palermo');
    expect(mockEstablishmentSummaryActive.status).toBe('ACTIVE');
    expect(mockEstablishmentSummaryActive.isOpen).toBe(true);

    expect(mockEstablishmentSummaryPaused.id).toBe(MOCK_TENANT_PAUSED_ID);
    expect(mockEstablishmentSummaryPaused.status).toBe('PAUSED');
    expect(mockEstablishmentSummaryPaused.isOpen).toBe(false);

    expect(mockEstablishmentSummaries.length).toBeGreaterThanOrEqual(3);
  });

  it('provides complete diner catalog response with categorized items and availability', () => {
    expect(mockDinerCatalogResponse.establishment.id).toBe(MOCK_TENANT_ID);
    expect(mockDinerCatalogResponse.categories).toHaveLength(2);

    const mainCourses = mockDinerCatalogResponse.categories[0];
    expect(mainCourses.category).toBe('Platos principales');
    expect(mainCourses.dishes.length).toBeGreaterThanOrEqual(2);

    const availableDish = mainCourses.dishes.find((d) => d.id === MOCK_DISH_ID_1);
    expect(availableDish).toBeDefined();
    expect(availableDish?.isAvailable).toBe(true);
    expect(availableDish?.recipeIngredients).toContain('Mozzarella');

    const outOfStockDish = mainCourses.dishes.find((d) => d.id === MOCK_DISH_ID_OUT_OF_STOCK);
    expect(outOfStockDish).toBeDefined();
    expect(outOfStockDish?.isAvailable).toBe(false);
  });

  it('creates mock establishment summaries with overrides', () => {
    const custom = createMockEstablishmentSummary({
      id: MOCK_TENANT_ID_2,
      name: 'Parrilla Gourmet',
      status: 'ACTIVE',
      isOpen: false,
    });

    expect(custom.id).toBe(MOCK_TENANT_ID_2);
    expect(custom.name).toBe('Parrilla Gourmet');
    expect(custom.isOpen).toBe(false);
  });

  it('creates mock dish catalog items and availability map with overrides', () => {
    const customDish = createMockDishCatalogItem({
      name: 'Ensalada Caesar',
      category: 'Ensaladas',
      price: 4200.0,
      isAvailable: false,
    });

    expect(customDish.name).toBe('Ensalada Caesar');
    expect(customDish.category).toBe('Ensaladas');
    expect(customDish.isAvailable).toBe(false);

    const customAvailability = createMockAvailabilityMap({
      'dish-123': false,
      'dish-456': true,
    });

    expect(customAvailability.availability['dish-123']).toBe(false);
    expect(customAvailability.availability['dish-456']).toBe(true);
  });

  it('sets up API mock handlers and correctly simulates search, catalog, and availability', async () => {
    const handlers = setupDinerCatalogApiMockHandlers();

    // Query < 3 chars returns empty array
    const emptyResults = await handlers.searchEstablishments('la');
    expect(emptyResults).toEqual([]);

    // Query >= 3 chars returns matching active establishments
    const searchResults = await handlers.searchEstablishments('Farola');
    expect(searchResults).toHaveLength(1);
    expect(searchResults[0].name).toBe('La Farola de Palermo');

    // Catalog response
    const catalog = await handlers.getEstablishmentCatalog(MOCK_TENANT_ID);
    expect(catalog.establishment.name).toBe('La Farola de Palermo');

    // Availability response
    const availability = await handlers.getCatalogAvailability(MOCK_TENANT_ID);
    expect(availability.availability[MOCK_DISH_ID_1]).toBe(true);
  });
});

