import React from 'react';

export interface AllergenStepProps {
  dishId?: string;
  initialRestrictions?: string[];
  initialConfirmed?: boolean;
  onSaveAllergens: (restrictionCodes: string[], confirmed: boolean) => Promise<void>;
  isReadOnly?: boolean;
  isSaving?: boolean;
}

export const AllergenStep: React.FC<AllergenStepProps> = () => {
  return <div data-testid="allergen-step-stub">AllergenStep Stub</div>;
};

export default AllergenStep;
