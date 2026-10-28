import React from 'react';
import type { CreateDishRequest } from '@/types/dish';

export interface BasicIdentityStepProps {
  initialData?: Partial<CreateDishRequest & { photoUrl?: string | null }>;
  onSave?: (data: CreateDishRequest, photoFile?: File | null) => Promise<void>;
  onPhotoDelete?: () => Promise<void>;
  isReadOnly?: boolean;
  isSaving?: boolean;
}

export const BasicIdentityStep: React.FC<BasicIdentityStepProps> = () => {
  return <div>Stub BasicIdentityStep - T032 pending</div>;
};

export default BasicIdentityStep;
