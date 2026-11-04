export interface DietaryRestrictionItem {
  code: string;
  name: string;
  category: 'DIETARY_LIFESTYLE' | 'COMMON_ALLERGEN' | string;
  description?: string | null;
}

export interface ProfileResponse {
  id: string;
  firstName?: string | null;
  lastName?: string | null;
  fullName?: string | null;
  phone?: string | null;
  email: string;
  pendingEmail?: string | null;
  emailVerificationPending?: boolean;
  age?: number | null;
  gender?: string | null;
  address?: string | null;
  avatarUrl?: string | null;
  dietaryRestrictions: DietaryRestrictionItem[];
}

export interface DemographicsRequest {
  age?: number | null;
  gender?: string | null;
  address?: string | null;
}

export interface AvatarUploadResponse {
  userId: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  avatarUrl: string;
}

export interface PersonalDataRequest {
  firstName: string;
  lastName: string;
  phone?: string | null;
}

export interface EmailChangeRequestDto {
  newEmail: string;
}

export interface EmailChangeInitiatedResponse {
  message: string;
  pendingEmail: string;
  expiresAt: string;
}

export interface EmailConfirmationRequest {
  token: string;
}

export interface EmailConfirmedResponse {
  message: string;
  activeEmail: string;
}
