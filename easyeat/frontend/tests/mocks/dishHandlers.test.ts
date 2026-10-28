import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  mockDishDraftSummary,
  mockDishPendingPriceSummary,
  mockDishDraftDetail,
  mockDishCompleteDetail,
  mockDishLockedDetail,
  createMockDishPage,
  createMockRecipeResponse,
  createMockAllergensResponse,
  createMockPhotoUploadResponse,
  createMockAdminNotification,
  createMockAdminNotificationPage,
  setupDishApiMockHandlers,
} from './dishHandlers';

describe('dishHandlers Mock Fixtures and Setup (T003)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('provides valid mock dish draft summary and pending price summary', () => {
    expect(mockDishDraftSummary.status).toBe('DRAFT');
    expect(mockDishDraftSummary.name).toBe('Risotto de Hongos Portobello');
    expect(mockDishDraftSummary.category).toBe('Platos principales');

    expect(mockDishPendingPriceSummary.status).toBe('PENDING_PRICE');
    expect(mockDishPendingPriceSummary.name).toBe('Pad Thai con Maní');
  });

  it('provides complete and locked dish detail fixtures', () => {
    expect(mockDishDraftDetail.status).toBe('DRAFT');
    expect(mockDishDraftDetail.isCompleteForReview).toBe(false);
    expect(mockDishDraftDetail.missingConditions.length).toBeGreaterThan(0);

    expect(mockDishCompleteDetail.status).toBe('DRAFT');
    expect(mockDishCompleteDetail.isCompleteForReview).toBe(true);
    expect(mockDishCompleteDetail.missingConditions).toHaveLength(0);

    expect(mockDishLockedDetail.status).toBe('PENDING_PRICE');
  });

  it('creates paginated dish responses with custom pagination overrides', () => {
    const page = createMockDishPage([mockDishDraftSummary, mockDishPendingPriceSummary], {
      pageNumber: 0,
      pageSize: 20,
      totalElements: 2,
    });

    expect(page.content).toHaveLength(2);
    expect(page.pageNumber).toBe(0);
    expect(page.pageSize).toBe(20);
    expect(page.totalElements).toBe(2);
    expect(page.totalPages).toBe(1);
    expect(page.isLast).toBe(true);
  });

  it('creates mock recipe, allergens and photo responses', () => {
    const recipe = createMockRecipeResponse('dish-1');
    expect(recipe.dishId).toBe('dish-1');
    expect(recipe.recipe.length).toBeGreaterThan(0);

    const allergens = createMockAllergensResponse('dish-1', ['CELIAC'], true);
    expect(allergens.dishId).toBe('dish-1');
    expect(allergens.allergensConfirmed).toBe(true);
    expect(allergens.restrictionCodes).toEqual(['CELIAC']);

    const photo = createMockPhotoUploadResponse('dish-1');
    expect(photo.dishId).toBe('dish-1');
    expect(photo.photoUrl).toContain('/api/v1/dishes/dish-1/photo');
  });

  it('creates admin notifications and notification page', () => {
    const notif = createMockAdminNotification();
    expect(notif.type).toBe('DISH_SUBMITTED_FOR_REVIEW');
    expect(notif.isRead).toBe(false);

    const page = createMockAdminNotificationPage([notif]);
    expect(page.content).toHaveLength(1);
    expect(page.unreadCount).toBe(1);
  });

  it('configures dish API mock handler suite with custom overrides', async () => {
    const mocks = setupDishApiMockHandlers({
      getDishDetail: vi.fn().mockResolvedValue(mockDishCompleteDetail),
    });

    const result = await mocks.getDishDetail('any-id');
    expect(result).toEqual(mockDishCompleteDetail);
    expect(mocks.getDishDetail).toHaveBeenCalledWith('any-id');
  });
});
