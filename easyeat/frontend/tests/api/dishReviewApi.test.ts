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
});
