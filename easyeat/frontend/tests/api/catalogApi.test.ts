import { describe, it, expect, vi, beforeEach } from 'vitest';
import { catalogApi } from '../../src/api/catalogApi';
import { apiClient } from '../../src/api/apiClient';

vi.mock('../../src/api/apiClient', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('catalogApi Client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls GET /establishments/:tenantId/dishes and returns enriched dishes', async () => {
    const mockDishes = [
      {
        id: 'dish-1',
        tenantId: 'tenant-1',
        name: 'Plato 1',
        description: null,
        price: 5000,
        isActive: true,
        isCompliant: true,
        conflictingRestrictions: [],
      },
    ];

    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockDishes });

    const result = await catalogApi.getEstablishmentDishes('tenant-1');

    expect(apiClient.get).toHaveBeenCalledWith('/establishments/tenant-1/dishes');
    expect(result).toEqual(mockDishes);
  });

  it('calls POST /cart/evaluate-compliance with dishIds and returns evaluation', async () => {
    const mockResponse = {
      items: [
        {
          dishId: 'dish-1',
          isCompliant: false,
          conflictingRestrictions: ['PEANUTS'],
        },
      ],
    };

    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await catalogApi.evaluateCartCompliance({ dishIds: ['dish-1'] });

    expect(apiClient.post).toHaveBeenCalledWith('/cart/evaluate-compliance', { dishIds: ['dish-1'] });
    expect(result).toEqual(mockResponse);
  });
});
