import React from 'react';
import type { DishDetailResponse } from '@/types/dish';

export interface ReviewSubmitStepProps {
  dish: DishDetailResponse | null;
  onSubmitForReview: () => Promise<void> | void;
  isReadOnly?: boolean;
  isSubmitting?: boolean;
}

export const ReviewSubmitStep: React.FC<ReviewSubmitStepProps> = () => {
  return <div data-testid="review-submit-step-stub">Stub</div>;
};

export default ReviewSubmitStep;
