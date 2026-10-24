import { describe, it, expect, beforeEach, vi } from 'vitest';
import { inventoryApi } from '@/api/inventoryApi';
import { apiClient } from '@/api/apiClient';
import type {
  AdminIngredientDetailResponse,
  CreateIngredientRequest,
  UpdateIngredientRequest,
  StockAdjustmentRequest,
  StockAdjustmentResponse,
  StockAuditEntryResponse,
  ChefIngredientPageResponse,
} from '@/types/inventory';

describe('inventoryApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('calls GET /admin/ingredients with category and status query params (FR-012, FR-031)', async () => {
    const mockIngredients: AdminIngredientDetailResponse[] = [
      {
        id: 'ing-1',
        tenantId: 'tenant-1',
        name: 'Tomate Redondo',
        category: 'Produce',
        unitOfMeasure: 'kg',
        unitPurchaseCost: 1500.5,
        status: 'ACTIVE',
        currentStock: 25.0,
        createdAt: '2026-10-24T10:00:00Z',
        updatedAt: '2026-10-24T10:00:00Z',
      },
    ];

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({
      data: mockIngredients,
    });

    const result = await inventoryApi.listIngredients({ category: 'Produce', status: 'ACTIVE' });

    expect(getSpy).toHaveBeenCalledWith('/admin/ingredients', {
      params: { category: 'Produce', status: 'ACTIVE' },
    });
    expect(result).toEqual(mockIngredients);
  });

  it('calls POST /admin/ingredients to create a new ingredient (FR-010)', async () => {
    const request: CreateIngredientRequest = {
      name: 'Harina 000',
      category: 'Dry Goods',
      unitOfMeasure: 'kg',
      unitPurchaseCost: 800.0,
    };

    const mockResponse: AdminIngredientDetailResponse = {
      id: 'ing-2',
      tenantId: 'tenant-1',
      name: 'Harina 000',
      category: 'Dry Goods',
      unitOfMeasure: 'kg',
      unitPurchaseCost: 800.0,
      status: 'ACTIVE',
      currentStock: 0,
      createdAt: '2026-10-24T11:00:00Z',
      updatedAt: '2026-10-24T11:00:00Z',
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({
      data: mockResponse,
    });

    const result = await inventoryApi.createIngredient(request);

    expect(postSpy).toHaveBeenCalledWith('/admin/ingredients', request);
    expect(result).toEqual(mockResponse);
  });

  it('calls PUT /admin/ingredients/{id} to update attributes (FR-012)', async () => {
    const request: UpdateIngredientRequest = {
      name: 'Tomate Perita',
      category: 'Produce',
      unitOfMeasure: 'kg',
      unitPurchaseCost: 1600.0,
    };

    const mockResponse: AdminIngredientDetailResponse = {
      id: 'ing-1',
      tenantId: 'tenant-1',
      name: 'Tomate Perita',
      category: 'Produce',
      unitOfMeasure: 'kg',
      unitPurchaseCost: 1600.0,
      status: 'ACTIVE',
      currentStock: 25.0,
      createdAt: '2026-10-24T10:00:00Z',
      updatedAt: '2026-10-24T11:30:00Z',
    };

    const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValueOnce({
      data: mockResponse,
    });

    const result = await inventoryApi.updateIngredient('ing-1', request);

    expect(putSpy).toHaveBeenCalledWith('/admin/ingredients/ing-1', request);
    expect(result).toEqual(mockResponse);
  });

  it('calls PATCH /admin/ingredients/{id}/status to transition lifecycle status (FR-014)', async () => {
    const mockResponse: AdminIngredientDetailResponse = {
      id: 'ing-1',
      tenantId: 'tenant-1',
      name: 'Tomate Redondo',
      category: 'Produce',
      unitOfMeasure: 'kg',
      unitPurchaseCost: 1500.5,
      status: 'DISCONTINUED',
      currentStock: 25.0,
      createdAt: '2026-10-24T10:00:00Z',
      updatedAt: '2026-10-24T12:00:00Z',
    };

    const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce({
      data: mockResponse,
    });

    const result = await inventoryApi.updateStatus('ing-1', 'DISCONTINUED');

    expect(patchSpy).toHaveBeenCalledWith('/admin/ingredients/ing-1/status', {
      targetStatus: 'DISCONTINUED',
    });
    expect(result).toEqual(mockResponse);
  });

  it('calls POST /admin/ingredients/{id}/stock-adjustments for manual adjustment (FR-015, FR-018)', async () => {
    const adjustmentRequest: StockAdjustmentRequest = {
      mode: 'RELATIVE',
      value: 10.5,
      justification: 'Compra en mercado central según remito 4589',
    };

    const mockResponse: StockAdjustmentResponse = {
      ingredientId: 'ing-1',
      ingredientName: 'Tomate Redondo',
      unitOfMeasure: 'kg',
      previousStock: 25.0,
      newStock: 35.5,
      netDelta: 10.5,
      auditEntryId: 'audit-entry-1',
      adjustedAt: '2026-10-24T12:15:00Z',
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({
      data: mockResponse,
    });

    const result = await inventoryApi.adjustStock('ing-1', adjustmentRequest);

    expect(postSpy).toHaveBeenCalledWith('/admin/ingredients/ing-1/stock-adjustments', adjustmentRequest);
    expect(result).toEqual(mockResponse);
  });

  it('calls GET /admin/ingredients/{id}/stock-audit to retrieve audit history (FR-019)', async () => {
    const mockAudit: StockAuditEntryResponse[] = [
      {
        id: 'audit-1',
        ingredientId: 'ing-1',
        userId: 'admin-1',
        adminName: 'Administrador Principal',
        adjustmentMode: 'RELATIVE',
        previousStock: 25.0,
        newStock: 35.5,
        netDelta: 10.5,
        justification: 'Compra en mercado central',
        createdAt: '2026-10-24T12:15:00Z',
      },
    ];

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({
      data: mockAudit,
    });

    const result = await inventoryApi.getAuditHistory('ing-1');

    expect(getSpy).toHaveBeenCalledWith('/admin/ingredients/ing-1/stock-audit');
    expect(result).toEqual(mockAudit);
  });

  it('calls GET /chef/ingredients with pagination and filter params (FR-021, FR-023)', async () => {
    const mockChefPage: ChefIngredientPageResponse = {
      items: [
        {
          id: 'ing-1',
          name: 'Tomate Redondo',
          category: 'Produce',
          unitOfMeasure: 'kg',
          stockStatus: 'In Stock',
          currentStock: 35.5,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    };

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({
      data: mockChefPage,
    });

    const result = await inventoryApi.getChefCatalog({ category: 'Produce', search: 'tomate', page: 0, size: 20 });

    expect(getSpy).toHaveBeenCalledWith('/chef/ingredients', {
      params: { category: 'Produce', search: 'tomate', page: 0, size: 20 },
    });
    expect(result).toEqual(mockChefPage);
  });
});
