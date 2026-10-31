import { describe, it, expect } from 'vitest';
import {
  DISH_CATEGORIES,
  isDishCategory,
  isDishStatus,
} from '@/types/dish';
import type {
  DishDetailResponse,
  DishSummaryResponse,
  DishPageResponse,
  CreateDishRequest,
  UpdateDishRequest,
  RecipeUpdateRequest,
  DishAllergensUpdateRequest,
  PhotoUploadResponse,
} from '@/types/dish';
import type {
  AdminNotificationResponse,
  AdminNotificationPageResponse,
} from '@/types/notification';

describe('Dish Types and Validation (T006)', () => {
  it('defines the 5 closed categories per spec CHF-2.2.1', () => {
    expect(DISH_CATEGORIES).toEqual([
      'Entradas',
      'Platos principales',
      'Postres',
      'Bebidas',
      'Ensaladas',
    ]);
  });

  it('validates DishCategory type guard', () => {
    expect(isDishCategory('Platos principales')).toBe(true);
    expect(isDishCategory('Entradas')).toBe(true);
    expect(isDishCategory('Postres')).toBe(true);
    expect(isDishCategory('Bebidas')).toBe(true);
    expect(isDishCategory('Ensaladas')).toBe(true);
    expect(isDishCategory('Pastas')).toBe(false);
    expect(isDishCategory(null)).toBe(false);
  });

  it('validates DishStatus type guard', () => {
    expect(isDishStatus('DRAFT')).toBe(true);
    expect(isDishStatus('PENDING_PRICE')).toBe(true);
    // T079a: PUBLISHED was rejected here before spec 007's T076 could ever set it on a dish;
    // the backend's DishStatus enum admitted the constant under T005's D1 amendment, and this
    // union disagreed with it until now.
    expect(isDishStatus('PUBLISHED')).toBe(true);
    expect(isDishStatus('')).toBe(false);
    expect(isDishStatus('ACTIVE')).toBe(false);
    expect(isDishStatus(null)).toBe(false);
  });

  it('ensures typing compatibility for DishDetailResponse and completeness fields', () => {
    const detail: DishDetailResponse = {
      id: 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
      tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      name: 'Risotto de Hongos',
      category: 'Platos principales',
      description: 'Cremoso risotto con portobello',
      photoUrl: '/api/v1/dishes/d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01/photo',
      status: 'DRAFT',
      allergensConfirmed: false,
      restrictionCodes: ['CELIAC'],
      recipe: [
        {
          ingredientId: 'i1eebc99-9c0b-4ef8-bb6d-6bb9bd380b01',
          ingredientName: 'Arroz Carnaroli',
          quantity: 250,
          unitOfMeasure: 'g',
        },
      ],
      isCompleteForReview: false,
      missingConditions: ['Se debe confirmar la sección de alérgenos'],
      createdById: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33',
      createdAt: '2026-10-27T12:00:00Z',
      updatedAt: '2026-10-27T12:30:00Z',
    };

    expect(detail.isCompleteForReview).toBe(false);
    expect(detail.missingConditions).toHaveLength(1);
  });

  it('ensures typing compatibility for request payloads and summary', () => {
    const createReq: CreateDishRequest = {
      name: 'Ensalada Fresca',
      category: 'Ensaladas',
      description: null,
    };
    const updateReq: UpdateDishRequest = {
      name: 'Ensalada Fresca',
      category: 'Ensaladas',
      description: 'Con aderezo casero',
    };
    const recipeReq: RecipeUpdateRequest = {
      recipe: [{ ingredientId: 'ing-1', quantity: 100 }],
    };
    const allergenReq: DishAllergensUpdateRequest = {
      restrictionCodes: ['VEGAN'],
      confirmed: true,
    };
    const photoRes: PhotoUploadResponse = {
      dishId: 'dish-1',
      fileName: 'photo.jpg',
      contentType: 'image/jpeg',
      fileSize: 1024,
      photoUrl: '/api/v1/dishes/dish-1/photo',
    };
    const summary: DishSummaryResponse = {
      id: 'dish-1',
      tenantId: 'tenant-1',
      name: 'Ensalada Fresca',
      category: 'Ensaladas',
      status: 'DRAFT',
      photoUrl: null,
      allergensConfirmed: true,
      recipeIngredientsCount: 1,
      createdAt: '2026-10-27T12:00:00Z',
      updatedAt: '2026-10-27T12:00:00Z',
    };
    const page: DishPageResponse = {
      content: [summary],
      pageNumber: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
      isLast: true,
    };

    expect(createReq.name).toBe('Ensalada Fresca');
    expect(updateReq.description).toBe('Con aderezo casero');
    expect(recipeReq.recipe).toHaveLength(1);
    expect(allergenReq.confirmed).toBe(true);
    expect(photoRes.fileSize).toBe(1024);
    expect(page.content).toHaveLength(1);
  });

  it('ensures typing compatibility for AdminNotificationResponse and pagination', () => {
    const notification: AdminNotificationResponse = {
      id: 'n1eebc99-9c0b-4ef8-bb6d-6bb9bd380c01',
      tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      type: 'DISH_SUBMITTED_FOR_REVIEW',
      title: 'Nuevo plato pendiente de costeo y fijación de precio',
      message: 'El Chef Carlos Chef ha enviado el plato "Risotto de Hongos" a revisión.',
      dishId: 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
      chefId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33',
      isRead: false,
      createdAt: '2026-10-27T12:30:00Z',
    };

    const notifPage: AdminNotificationPageResponse = {
      content: [notification],
      pageNumber: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
      isLast: true,
      unreadCount: 1,
    };

    expect(notification.type).toBe('DISH_SUBMITTED_FOR_REVIEW');
    expect(notifPage.unreadCount).toBe(1);
  });
});
