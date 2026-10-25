import React from 'react';

export interface PersonalDataFormProps {
  initialData?: {
    firstName?: string;
    lastName?: string;
    phone?: string;
    email?: string;
  };
  onSubmit: (data: { firstName: string; lastName: string; phone?: string }) => Promise<void>;
  onCancel?: () => void;
  onRequestEmailChange?: (newEmail: string) => Promise<void>;
  isSubmitting?: boolean;
}

export const PersonalDataForm: React.FC<PersonalDataFormProps> = () => {
  return <div>Stub PersonalDataForm</div>;
};
