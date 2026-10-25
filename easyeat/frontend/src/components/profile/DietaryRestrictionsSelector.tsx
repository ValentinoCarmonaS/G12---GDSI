import React from 'react';
import type { DietaryRestrictionItem } from '../../types/profile';

export interface DietaryRestrictionsSelectorProps {
  availableTaxonomy: DietaryRestrictionItem[];
  selectedCodes: string[];
  onSave: (codes: string[]) => Promise<void>;
  loading?: boolean;
}

export const DietaryRestrictionsSelector: React.FC<DietaryRestrictionsSelectorProps> = () => {
  return <div data-testid="dietary-restrictions-selector" />;
};

export default DietaryRestrictionsSelector;
