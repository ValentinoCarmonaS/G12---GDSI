import { apiClient } from './apiClient';
import type {
  ApproveDishRequest,
  DishApprovalResult,
  DishPricingResult,
  DishRejectionResult,
  DishReviewDetail,
  DishReviewInboxPage,
  InboxTab,
  RejectDishRequest,
  SetRetailPriceRequest,
} from '@/types/dishReview';

/** FR-003: the page size the contract publishes as the default. */
const DEFAULT_PAGE_SIZE = 20;

export interface InboxQuery {
  tab?: InboxTab;
  page?: number;
  size?: number;
}

/**
 * Typed client for the Administrative Back-Office read endpoints (spec 007).
 *
 * Thin by intent. It shapes requests, unwraps bodies and does nothing else: no retries, no
 * caching, no defaulting of error responses. An inbox that rendered empty after a 403 would tell
 * the Administrator there is nothing to review, which is both false and worse than an error.
 */
export const dishReviewApi = {
  /**
   * The segmented review inbox (FR-002 to FR-005).
   *
   * The tab is sent verbatim. A value outside the enum is refused by the server with a 400, and
   * normalising it here would turn a caller's bug into a working-looking default view.
   */
  async getInbox(query: InboxQuery = {}): Promise<DishReviewInboxPage> {
    const response = await apiClient.get<DishReviewInboxPage>('/admin/dishes/review-inbox', {
      params: {
        tab: query.tab ?? 'PENDING',
        page: query.page ?? 0,
        size: query.size ?? DEFAULT_PAGE_SIZE,
      },
    });
    return response.data;
  },

  /**
   * The complete culinary file with the itemized Food Cost audit (FR-006).
   *
   * The identifier is encoded: it reaches this function from a route parameter, and a value
   * carrying path separators must not be able to reshape the URL.
   */
  async getDetail(dishId: string): Promise<DishReviewDetail> {
    const response = await apiClient.get<DishReviewDetail>(
      `/admin/dishes/${encodeURIComponent(dishId)}/review-detail`
    );
    return response.data;
  },

  /**
   * Assigns or updates the retail price (FR-009 to FR-014, FR-025).
   *
   * `expectedRetailPrice` is sent exactly as the caller passed it, `null` included: that is the
   * positive assertion "the dish was unpriced when I read it", not an omission, and the contract
   * rejects an actually-omitted field with 400.
   */
  async setRetailPrice(
    dishId: string,
    request: SetRetailPriceRequest
  ): Promise<DishPricingResult> {
    const response = await apiClient.put<DishPricingResult>(
      `/admin/dishes/${encodeURIComponent(dishId)}/retail-price`,
      request
    );
    return response.data;
  },

  /**
   * Evaluates the publication gate and publishes the dish (FR-015 to FR-019).
   *
   * The gate itself is re-evaluated server-side; this call carries only the acknowledgements the
   * Administrator already confirmed, never a gate condition.
   */
  async approve(dishId: string, request: ApproveDishRequest): Promise<DishApprovalResult> {
    const response = await apiClient.post<DishApprovalResult>(
      `/admin/dishes/${encodeURIComponent(dishId)}/approval`,
      request
    );
    return response.data;
  },

  /**
   * Rejects the dish and returns it to the kitchen with mandatory feedback (FR-020 to FR-022).
   *
   * The comment is sent exactly as typed, never pre-trimmed: FR-021 stores it verbatim.
   */
  async reject(dishId: string, request: RejectDishRequest): Promise<DishRejectionResult> {
    const response = await apiClient.post<DishRejectionResult>(
      `/admin/dishes/${encodeURIComponent(dishId)}/rejection`,
      request
    );
    return response.data;
  },

  /**
   * Cancels a dish proposal (C-02).
   */
  async cancel(dishId: string): Promise<void> {
    await apiClient.post(`/admin/dishes/${encodeURIComponent(dishId)}/cancel`);
  },
};
