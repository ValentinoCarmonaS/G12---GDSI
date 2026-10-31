import { apiClient } from './apiClient';
import type {
  DishReviewDetail,
  DishReviewInboxPage,
  InboxTab,
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
};
