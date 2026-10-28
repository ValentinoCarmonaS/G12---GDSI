import type {
  CreateDishRequest,
  UpdateDishRequest,
  DishDetailResponse,
  DishPageResponse,
  PhotoUploadResponse,
} from '@/types/dish';

export const dishApi = {
  listDishes: async (_params?: { status?: string; page?: number; size?: number }): Promise<DishPageResponse> => {
    throw new Error('dishApi.listDishes not implemented yet (T030)');
  },
  createDishDraft: async (_data: CreateDishRequest): Promise<DishDetailResponse> => {
    throw new Error('dishApi.createDishDraft not implemented yet (T030)');
  },
  getDishDetail: async (_id: string): Promise<DishDetailResponse> => {
    throw new Error('dishApi.getDishDetail not implemented yet (T030)');
  },
  updateDish: async (_id: string, _data: UpdateDishRequest): Promise<DishDetailResponse> => {
    throw new Error('dishApi.updateDish not implemented yet (T030)');
  },
  deleteDish: async (_id: string): Promise<void> => {
    throw new Error('dishApi.deleteDish not implemented yet (T030)');
  },
  uploadPhoto: async (_id: string, _file: File): Promise<PhotoUploadResponse> => {
    throw new Error('dishApi.uploadPhoto not implemented yet (T030)');
  },
  deletePhoto: async (_id: string): Promise<void> => {
    throw new Error('dishApi.deletePhoto not implemented yet (T030)');
  },
};

export default dishApi;
