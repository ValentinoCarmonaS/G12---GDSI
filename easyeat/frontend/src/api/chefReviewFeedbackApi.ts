import { apiClient } from './apiClient';
import type { ChefReviewFeedback } from '@/types/dishReview';

/**
 * Typed client for the Chef's own rejection-feedback read (FR-023, SC-003). Separate from {@code
 * dishReviewApi}, which is scoped to the Administrative Back-Office surface: this hits a
 * different role's endpoint and carries no admin-facing shape.
 */
export const chefReviewFeedbackApi = {
  async getFeedback(dishId: string): Promise<ChefReviewFeedback> {
    const response = await apiClient.get<ChefReviewFeedback>(
      `/dishes/${encodeURIComponent(dishId)}/review-feedback`
    );
    return response.data;
  },
};
