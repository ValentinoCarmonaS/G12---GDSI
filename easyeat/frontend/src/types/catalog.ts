export interface EnrichedDish {
  id: string;
  tenantId: string;
  name: string;
  description: string | null;
  price: number;
  isActive: boolean;
  isCompliant: boolean;
  conflictingRestrictions: string[];
}
