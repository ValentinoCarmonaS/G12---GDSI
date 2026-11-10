export type ContrastColor = '#000000' | '#FFFFFF';

export interface RestaurantBranding {
  establishmentId: string;
  fantasyName: string | null;
  description: string | null;
  logoUrl: string | null;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
  primaryContrastColor: ContrastColor;
  secondaryContrastColor: ContrastColor;
  accentContrastColor: ContrastColor;
  isDefaultFallback?: boolean;
}

export interface AdminBrandingResponse extends RestaurantBranding {
  updatedAt: string;
}

export interface UpdateBrandingRequest {
  fantasyName?: string | null;
  description?: string | null;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
}

export interface BrandingLogoUploadResponse {
  logoUrl: string;
  message: string;
}
