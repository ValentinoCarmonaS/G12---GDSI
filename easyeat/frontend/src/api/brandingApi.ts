import { apiClient } from './apiClient';

export interface RestaurantBranding {
  establishmentId: string;
  fantasyName: string;
  description: string;
  logoUrl?: string | null;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
  primaryContrastColor: string;
  secondaryContrastColor: string;
  accentContrastColor: string;
  updatedAt: string;
  isDefaultFallback: boolean;
}

export interface UpdateRestaurantBrandingPayload {
  fantasyName: string;
  description?: string;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
}

export interface LogoUploadResponse {
  logoUrl: string;
  updatedAt: string;
}

/**
 * Fetches establishment branding settings for the authenticated administrator.
 */
export async function getAdminBranding(establishmentId: string): Promise<RestaurantBranding> {
  const response = await apiClient.get<RestaurantBranding>(`/admin/establishments/${establishmentId}/branding`);
  return response.data;
}

/**
 * Updates establishment visual triad and commercial identity.
 */
export async function updateAdminBranding(
  establishmentId: string,
  payload: UpdateRestaurantBrandingPayload
): Promise<RestaurantBranding> {
  const response = await apiClient.put<RestaurantBranding>(
    `/admin/establishments/${establishmentId}/branding`,
    payload
  );
  return response.data;
}

/**
 * Uploads restaurant logo file (PNG, JPG, WEBP, SVG).
 */
export async function uploadRestaurantLogo(
  establishmentId: string,
  file: File
): Promise<LogoUploadResponse> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await apiClient.post<LogoUploadResponse>(
    `/admin/establishments/${establishmentId}/branding/logo`,
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }
  );
  return response.data;
}

/**
 * Public endpoint to fetch visual branding for diner views with neutral fallback.
 */
export async function getDinerBranding(establishmentId: string): Promise<RestaurantBranding> {
  const response = await apiClient.get<RestaurantBranding>(`/establishments/${establishmentId}/branding`);
  return response.data;
}
