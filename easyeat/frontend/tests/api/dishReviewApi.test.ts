import { describe, it, expect, vi, beforeEach } from 'vitest';
import { dishReviewApi } from '../../src/api/dishReviewApi';
import { apiClient } from '../../src/api/apiClient';
import type { DishReviewInboxPage, DishReviewDetail } from '../../src/types/dishReview';

vi.mock('../../src/api/apiClient', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

const emptyPage: DishReviewInboxPage = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
  pendingCount: 0,
};

describe('dishReviewApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('request shaping', () => {
    it('calls GET /admin/dishes/review-inbox with tab, page and size as query params', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: emptyPage });

      await dishReviewApi.getInbox({ tab: 'PENDING', page: 2, size: 20 });

      expect(apiClient.get).toHaveBeenCalledWith('/admin/dishes/review-inbox', {
        params: { tab: 'PENDING', page: 2, size: 20 },
      });
    });

    it('defaults to the PENDING tab, page 0 and 20 per page (FR-003)', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: emptyPage });

      await dishReviewApi.getInbox();

      expect(apiClient.get).toHaveBeenCalledWith('/admin/dishes/review-inbox', {
        params: { tab: 'PENDING', page: 0, size: 20 },
      });
    });

    it('sends the tab verbatim, so an unknown value reaches the server to be refused', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: emptyPage });

      // The server answers 400 for a tab outside the enum. The client must not silently
      // normalise it, or a bug in the caller would look like a working default view.
      await dishReviewApi.getInbox({ tab: 'HISTORY' });

      expect(apiClient.get).toHaveBeenCalledWith(
        '/admin/dishes/review-inbox',
        expect.objectContaining({ params: expect.objectContaining({ tab: 'HISTORY' }) })
      );
    });

    it('calls GET /admin/dishes/:id/review-detail with the id in the path', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: { dishId: 'dish-1' } });

      await dishReviewApi.getDetail('dish-1');

      expect(apiClient.get).toHaveBeenCalledWith('/admin/dishes/dish-1/review-detail');
    });

    it('encodes the identifier, so a malformed one cannot alter the path', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: {} });

      await dishReviewApi.getDetail('../../auth/users');

      expect(apiClient.get).toHaveBeenCalledWith(
        '/admin/dishes/..%2F..%2Fauth%2Fusers/review-detail'
      );
    });
  });

  describe('response handling', () => {
    it('returns the inbox page body unwrapped', async () => {
      const page: DishReviewInboxPage = { ...emptyPage, pendingCount: 37, totalElements: 37 };
      vi.mocked(apiClient.get).mockResolvedValue({ data: page });

      await expect(dishReviewApi.getInbox()).resolves.toEqual(page);
    });

    it('returns the detail body unwrapped, including a null audit (FR-037)', async () => {
      const detail = {
        dishId: 'dish-1',
        recipeAbsent: true,
        foodCostAudit: null,
      } as unknown as DishReviewDetail;
      vi.mocked(apiClient.get).mockResolvedValue({ data: detail });

      const result = await dishReviewApi.getDetail('dish-1');

      expect(result.recipeAbsent).toBe(true);
      expect(result.foodCostAudit).toBeNull();
    });
  });

  describe('error propagation', () => {
    it('propagates a server error rather than swallowing it into a default value', async () => {
      const error = Object.assign(new Error('Request failed'), {
        response: { status: 403, data: { type: 'https://easyeat.com/errors/forbidden' } },
      });
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(dishReviewApi.getInbox()).rejects.toBe(error);
    });

    it('propagates a 404 from the detail call, so the page can distinguish it', async () => {
      const error = Object.assign(new Error('Not found'), { response: { status: 404 } });
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(dishReviewApi.getDetail('missing')).rejects.toBe(error);
    });

    it('does not retry on failure: a retry would double-count an audited read', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(new Error('boom'));

      await expect(dishReviewApi.getInbox()).rejects.toThrow('boom');
      expect(apiClient.get).toHaveBeenCalledTimes(1);
    });
  });

  /**
   * T105/T106: the three write operations the contract defines
   * (`contracts/openapi.yaml`) for which no client method existed until now, which is why
   * `/admin/dishes` could render the publication gate's status but offered nothing to act on it
   * (HANDOFF-post-merge-ui-gap.md §2).
   */
  describe('setRetailPrice (FR-009, FR-013, FR-025)', () => {
    it('calls PUT /admin/dishes/:id/retail-price with the id in the path and the body verbatim', async () => {
      vi.mocked(apiClient.put).mockResolvedValue({ data: { dishId: 'dish-1' } });

      await dishReviewApi.setRetailPrice('dish-1', {
        retailPrice: 11250,
        expectedRetailPrice: null,
      });

      expect(apiClient.put).toHaveBeenCalledWith('/admin/dishes/dish-1/retail-price', {
        retailPrice: 11250,
        expectedRetailPrice: null,
      });
    });

    it('sends expectedRetailPrice as null rather than omitting it, which asserts the dish was unpriced', async () => {
      vi.mocked(apiClient.put).mockResolvedValue({ data: {} });

      await dishReviewApi.setRetailPrice('dish-1', {
        retailPrice: 5000,
        expectedRetailPrice: null,
      });

      expect(apiClient.put).toHaveBeenCalledWith(
        '/admin/dishes/dish-1/retail-price',
        expect.objectContaining({ expectedRetailPrice: null })
      );
    });

    it('forwards acknowledgeNegativeMargin when the Administrator confirmed the advisory', async () => {
      vi.mocked(apiClient.put).mockResolvedValue({ data: {} });

      await dishReviewApi.setRetailPrice('dish-1', {
        retailPrice: 100,
        expectedRetailPrice: 4500,
        acknowledgeNegativeMargin: true,
      });

      expect(apiClient.put).toHaveBeenCalledWith(
        '/admin/dishes/dish-1/retail-price',
        expect.objectContaining({ acknowledgeNegativeMargin: true })
      );
    });

    it('encodes the identifier, so a malformed one cannot alter the path', async () => {
      vi.mocked(apiClient.put).mockResolvedValue({ data: {} });

      await dishReviewApi.setRetailPrice('../../auth/users', {
        retailPrice: 100,
        expectedRetailPrice: null,
      });

      expect(apiClient.put).toHaveBeenCalledWith(
        '/admin/dishes/..%2F..%2Fauth%2Fusers/retail-price',
        expect.anything()
      );
    });

    it('returns the pricing result body unwrapped', async () => {
      const result = { dishId: 'dish-1', status: 'PENDING_PRICE', priceHistoryEntryId: 'h-1' };
      vi.mocked(apiClient.put).mockResolvedValue({ data: result });

      await expect(
        dishReviewApi.setRetailPrice('dish-1', { retailPrice: 100, expectedRetailPrice: null })
      ).resolves.toEqual(result);
    });

    it('propagates a 409 dish-stale-price conflict rather than swallowing it', async () => {
      const error = Object.assign(new Error('Conflict'), {
        response: {
          status: 409,
          data: { type: 'https://easyeat.com/errors/dish-stale-price', currentRetailPrice: 120 },
        },
      });
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(
        dishReviewApi.setRetailPrice('dish-1', { retailPrice: 100, expectedRetailPrice: null })
      ).rejects.toBe(error);
    });
  });

  describe('approve (FR-015 to FR-019)', () => {
    it('calls POST /admin/dishes/:id/approval with the body verbatim', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: { dishId: 'dish-1' } });

      await dishReviewApi.approve('dish-1', { acknowledgeUnpricedIngredients: true });

      expect(apiClient.post).toHaveBeenCalledWith('/admin/dishes/dish-1/approval', {
        acknowledgeUnpricedIngredients: true,
      });
    });

    it('sends an empty body when no acknowledgement is required, never omitting the call', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: {} });

      await dishReviewApi.approve('dish-1', {});

      expect(apiClient.post).toHaveBeenCalledWith('/admin/dishes/dish-1/approval', {});
    });

    it('encodes the identifier, so a malformed one cannot alter the path', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: {} });

      await dishReviewApi.approve('../../auth/users', {});

      expect(apiClient.post).toHaveBeenCalledWith(
        '/admin/dishes/..%2F..%2Fauth%2Fusers/approval',
        {}
      );
    });

    it('returns the approval result body unwrapped, including alreadyPublished (FR-018)', async () => {
      const result = {
        dishId: 'dish-1',
        status: 'PUBLISHED',
        alreadyPublished: true,
        publishedAt: '2026-10-01T10:00:00Z',
        reviewHistoryEntryId: null,
      };
      vi.mocked(apiClient.post).mockResolvedValue({ data: result });

      await expect(dishReviewApi.approve('dish-1', {})).resolves.toEqual(result);
    });

    it('propagates a 422 publication-gate-failed rather than swallowing it', async () => {
      const error = Object.assign(new Error('Unprocessable'), {
        response: {
          status: 422,
          data: {
            type: 'https://easyeat.com/errors/publication-gate-failed',
            unmetConditions: ['EMPTY_RECIPE'],
          },
        },
      });
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(dishReviewApi.approve('dish-1', {})).rejects.toBe(error);
    });

    it('propagates a 409 dish-already-decided conflict rather than swallowing it', async () => {
      const error = Object.assign(new Error('Conflict'), {
        response: {
          status: 409,
          data: { type: 'https://easyeat.com/errors/dish-already-decided', currentStatus: 'DRAFT' },
        },
      });
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(dishReviewApi.approve('dish-1', {})).rejects.toBe(error);
    });
  });

  describe('reject (FR-020 to FR-022)', () => {
    it('calls POST /admin/dishes/:id/rejection with the comment verbatim, never pre-trimmed', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: { dishId: 'dish-1' } });

      await dishReviewApi.reject('dish-1', { comment: '  Falta la foto del plato  ' });

      expect(apiClient.post).toHaveBeenCalledWith('/admin/dishes/dish-1/rejection', {
        comment: '  Falta la foto del plato  ',
      });
    });

    it('encodes the identifier, so a malformed one cannot alter the path', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: {} });

      await dishReviewApi.reject('../../auth/users', { comment: 'x'.repeat(10) });

      expect(apiClient.post).toHaveBeenCalledWith(
        '/admin/dishes/..%2F..%2Fauth%2Fusers/rejection',
        expect.anything()
      );
    });

    it('returns the rejection result body unwrapped', async () => {
      const result = {
        dishId: 'dish-1',
        status: 'DRAFT',
        cycleNumber: 1,
        reviewHistoryEntryId: 'h-1',
      };
      vi.mocked(apiClient.post).mockResolvedValue({ data: result });

      await expect(
        dishReviewApi.reject('dish-1', { comment: 'x'.repeat(10) })
      ).resolves.toEqual(result);
    });

    it('propagates a 409 dish-already-decided conflict rather than swallowing it', async () => {
      const error = Object.assign(new Error('Conflict'), {
        response: {
          status: 409,
          data: { type: 'https://easyeat.com/errors/dish-already-decided', currentStatus: 'PUBLISHED' },
        },
      });
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        dishReviewApi.reject('dish-1', { comment: 'x'.repeat(10) })
      ).rejects.toBe(error);
    });
  });
});
