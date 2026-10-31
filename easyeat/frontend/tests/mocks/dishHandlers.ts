import { vi } from 'vitest';
import type {
  DishSummaryResponse,
  DishDetailResponse,
  DishPageResponse,
  RecipeIngredientItem,
  RecipeResponse,
  DishAllergensResponse,
  PhotoUploadResponse,
  CreateDishRequest,
  UpdateDishRequest,
  RecipeUpdateRequest,
  DishAllergensUpdateRequest,
} from '@/types/dish';
import type {
  AdminNotificationResponse,
  AdminNotificationPageResponse,
} from '@/types/notification';

export const MOCK_TENANT_ID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';
export const MOCK_CHEF_ID = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33';
export const MOCK_ADMIN_ID = 'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44';
export const MOCK_DISH_ID_1 = 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01';
export const MOCK_DISH_ID_2 = 'd2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02';

export const mockRecipeIngredients: RecipeIngredientItem[] = [
  {
    ingredientId: 'i1eebc99-9c0b-4ef8-bb6d-6bb9bd380b01',
    ingredientName: 'Arroz Carnaroli',
    quantity: 250,
    unitOfMeasure: 'g',
  },
  {
    ingredientId: 'i2eebc99-9c0b-4ef8-bb6d-6bb9bd380b02',
    ingredientName: 'Hongos Portobello',
    quantity: 150,
    unitOfMeasure: 'g',
  },
  {
    ingredientId: 'i3eebc99-9c0b-4ef8-bb6d-6bb9bd380b03',
    ingredientName: 'Caldo de Verduras',
    quantity: 500,
    unitOfMeasure: 'ml',
  },
];

export const mockDishDraftSummary: DishSummaryResponse = {
  id: MOCK_DISH_ID_1,
  tenantId: MOCK_TENANT_ID,
  name: 'Risotto de Hongos Portobello',
  category: 'Platos principales',
  status: 'DRAFT',
  photoUrl: null,
  allergensConfirmed: false,
  recipeIngredientsCount: 3,
  createdAt: '2026-10-27T10:00:00Z',
  updatedAt: '2026-10-27T10:30:00Z',
};

export const mockDishPendingPriceSummary: DishSummaryResponse = {
  id: MOCK_DISH_ID_2,
  tenantId: MOCK_TENANT_ID,
  name: 'Pad Thai con Maní',
  category: 'Platos principales',
  status: 'PENDING_PRICE',
  photoUrl: `/api/v1/dishes/${MOCK_DISH_ID_2}/photo`,
  allergensConfirmed: true,
  recipeIngredientsCount: 4,
  createdAt: '2026-10-27T09:00:00Z',
  updatedAt: '2026-10-27T09:45:00Z',
};

export const mockDishDraftDetail: DishDetailResponse = {
  id: MOCK_DISH_ID_1,
  tenantId: MOCK_TENANT_ID,
  name: 'Risotto de Hongos Portobello',
  category: 'Platos principales',
  description: 'Arroz carnaroli cremoso con hongos portobello salteados al vino blanco.',
  photoUrl: null,
  status: 'DRAFT',
  allergensConfirmed: false,
  restrictionCodes: ['CELIAC'],
  recipe: mockRecipeIngredients,
  isCompleteForReview: false,
  missingConditions: ['Se debe confirmar la sección de alérgenos'],
  createdById: MOCK_CHEF_ID,
  createdAt: '2026-10-27T10:00:00Z',
  updatedAt: '2026-10-27T10:30:00Z',
};

export const mockDishCompleteDetail: DishDetailResponse = {
  ...mockDishDraftDetail,
  allergensConfirmed: true,
  isCompleteForReview: true,
  missingConditions: [],
};

export const mockDishLockedDetail: DishDetailResponse = {
  ...mockDishCompleteDetail,
  id: MOCK_DISH_ID_2,
  name: 'Pad Thai con Maní',
  status: 'PENDING_PRICE',
  photoUrl: `/api/v1/dishes/${MOCK_DISH_ID_2}/photo`,
  isCompleteForReview: true,
  missingConditions: [],
};

export function createMockDishPage(
  items: DishSummaryResponse[] = [mockDishDraftSummary, mockDishPendingPriceSummary],
  options: { pageNumber?: number; pageSize?: number; totalElements?: number } = {}
): DishPageResponse {
  const pageNumber = options.pageNumber ?? 0;
  const pageSize = options.pageSize ?? 20;
  const totalElements = options.totalElements ?? items.length;
  const totalPages = Math.ceil(totalElements / pageSize) || 1;

  return {
    content: items,
    pageNumber,
    pageSize,
    totalElements,
    totalPages,
    isLast: pageNumber >= totalPages - 1,
  };
}

export function createMockRecipeResponse(
  dishId: string = MOCK_DISH_ID_1,
  recipe: RecipeIngredientItem[] = mockRecipeIngredients
): RecipeResponse {
  return {
    dishId,
    recipe,
    updatedAt: new Date().toISOString(),
  };
}

export function createMockAllergensResponse(
  dishId: string = MOCK_DISH_ID_1,
  restrictionCodes: string[] = ['CELIAC'],
  allergensConfirmed: boolean = true
): DishAllergensResponse {
  return {
    dishId,
    restrictionCodes,
    allergensConfirmed,
    updatedAt: new Date().toISOString(),
  };
}

export function createMockPhotoUploadResponse(dishId: string = MOCK_DISH_ID_1): PhotoUploadResponse {
  return {
    dishId,
    fileName: 'dish-photo.jpg',
    contentType: 'image/jpeg',
    fileSize: 204800, // 200 KB
    photoUrl: `/api/v1/dishes/${dishId}/photo`,
  };
}

export function createMockAdminNotification(
  overrides?: Partial<AdminNotificationResponse>
): AdminNotificationResponse {
  return {
    id: 'n1eebc99-9c0b-4ef8-bb6d-6bb9bd380c01',
    tenantId: MOCK_TENANT_ID,
    type: 'DISH_SUBMITTED_FOR_REVIEW',
    title: 'Nuevo plato pendiente de costeo y fijación de precio',
    message: 'El Chef ha enviado el plato "Risotto de Hongos Portobello" a revisión.',
    dishId: MOCK_DISH_ID_1,
    chefId: MOCK_CHEF_ID,
    isRead: false,
    createdAt: '2026-10-27T10:35:00Z',
    ...overrides,
  };
}

export function createMockAdminNotificationPage(
  items: AdminNotificationResponse[] = [createMockAdminNotification()],
  options: { pageNumber?: number; pageSize?: number; totalElements?: number; unreadCount?: number } = {}
): AdminNotificationPageResponse {
  const pageNumber = options.pageNumber ?? 0;
  const pageSize = options.pageSize ?? 20;
  const totalElements = options.totalElements ?? items.length;
  const totalPages = Math.ceil(totalElements / pageSize) || 1;
  const unreadCount = options.unreadCount ?? items.filter((i) => !i.isRead).length;

  return {
    content: items,
    pageNumber,
    pageSize,
    totalElements,
    totalPages,
    isLast: pageNumber >= totalPages - 1,
    unreadCount,
  };
}

export interface DishApiMockHandlers {
  listDishes: ReturnType<typeof vi.fn>;
  createDishDraft: ReturnType<typeof vi.fn>;
  getDishDetail: ReturnType<typeof vi.fn>;
  updateDish: ReturnType<typeof vi.fn>;
  deleteDish: ReturnType<typeof vi.fn>;
  updateRecipe: ReturnType<typeof vi.fn>;
  deleteRecipeIngredient: ReturnType<typeof vi.fn>;
  updateAllergens: ReturnType<typeof vi.fn>;
  submitForReview: ReturnType<typeof vi.fn>;
  uploadPhoto: ReturnType<typeof vi.fn>;
  deletePhoto: ReturnType<typeof vi.fn>;
  listAdminNotifications: ReturnType<typeof vi.fn>;
  markNotificationRead: ReturnType<typeof vi.fn>;
}

export function setupDishApiMockHandlers(
  overrides?: Partial<DishApiMockHandlers>
): DishApiMockHandlers {
  const defaultHandlers: DishApiMockHandlers = {
    listDishes: vi.fn().mockResolvedValue(createMockDishPage()),
    createDishDraft: vi.fn().mockImplementation((data: CreateDishRequest) =>
      Promise.resolve({
        ...mockDishDraftDetail,
        name: data.name,
        category: data.category,
        description: data.description ?? null,
      })
    ),
    getDishDetail: vi.fn().mockResolvedValue(mockDishDraftDetail),
    updateDish: vi.fn().mockImplementation((_id: string, data: UpdateDishRequest) =>
      Promise.resolve({
        ...mockDishDraftDetail,
        name: data.name,
        category: data.category,
        description: data.description ?? null,
      })
    ),
    deleteDish: vi.fn().mockResolvedValue(undefined),
    updateRecipe: vi.fn().mockImplementation((id: string, _req: RecipeUpdateRequest) =>
      Promise.resolve(createMockRecipeResponse(id))
    ),
    deleteRecipeIngredient: vi.fn().mockResolvedValue(undefined),
    updateAllergens: vi.fn().mockImplementation((id: string, req: DishAllergensUpdateRequest) =>
      Promise.resolve(createMockAllergensResponse(id, req.restrictionCodes, req.confirmed))
    ),
    submitForReview: vi.fn().mockResolvedValue(mockDishLockedDetail),
    uploadPhoto: vi.fn().mockImplementation((id: string) =>
      Promise.resolve(createMockPhotoUploadResponse(id))
    ),
    deletePhoto: vi.fn().mockResolvedValue(undefined),
    listAdminNotifications: vi.fn().mockResolvedValue(createMockAdminNotificationPage()),
    markNotificationRead: vi.fn().mockImplementation((id: string) =>
      Promise.resolve(createMockAdminNotification({ id, isRead: true }))
    ),
  };

  return {
    ...defaultHandlers,
    ...overrides,
  };
}
