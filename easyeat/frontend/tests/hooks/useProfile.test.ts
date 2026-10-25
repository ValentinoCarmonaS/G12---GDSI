import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useProfile } from '@/hooks/useProfile';
import { profileApi } from '@/api/profileApi';
import type { ProfileResponse, DietaryRestrictionItem } from '@/types/profile';

describe('useProfile custom hook (T021, COM-1.4.1 & COM-1.4.3)', () => {
  const mockProfile: ProfileResponse = {
    id: 'uuid-1',
    firstName: 'Valentino',
    lastName: 'Carmona',
    fullName: 'Valentino Carmona',
    phone: '+5491123456789',
    email: 'valen@easyeat.com',
    pendingEmail: null,
    emailVerificationPending: false,
    dietaryRestrictions: [],
  };

  const mockTaxonomy: DietaryRestrictionItem[] = [
    { code: 'CELIAC', name: 'Sin TACC / Celíaco', category: 'DIETARY_LIFESTYLE' },
    { code: 'PEANUTS', name: 'Maní', category: 'COMMON_ALLERGEN' },
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches profile and taxonomy on mount and sets loading state', async () => {
    vi.spyOn(profileApi, 'getProfile').mockResolvedValueOnce(mockProfile);
    vi.spyOn(profileApi, 'getDietaryTaxonomy').mockResolvedValueOnce(mockTaxonomy);

    const { result } = renderHook(() => useProfile());

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    await waitFor(() => expect(result.current.taxonomyLoading).toBe(false));

    expect(result.current.profile).toEqual(mockProfile);
    expect(result.current.taxonomy).toEqual(mockTaxonomy);
    expect(result.current.error).toBeNull();
  });

  it('updates personal data and updates profile state', async () => {
    vi.spyOn(profileApi, 'getProfile').mockResolvedValueOnce(mockProfile);
    vi.spyOn(profileApi, 'getDietaryTaxonomy').mockResolvedValueOnce(mockTaxonomy);
    const updatedProfile: ProfileResponse = {
      ...mockProfile,
      firstName: 'Agustin',
      lastName: 'Fraga',
      fullName: 'Agustin Fraga',
    };
    vi.spyOn(profileApi, 'updatePersonalData').mockResolvedValueOnce(updatedProfile);

    const { result } = renderHook(() => useProfile());
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.updatePersonalData({
        firstName: 'Agustin',
        lastName: 'Fraga',
      });
    });

    expect(result.current.profile).toEqual(updatedProfile);
  });

  it('updates dietary restrictions and reflects changes in profile state', async () => {
    vi.spyOn(profileApi, 'getProfile').mockResolvedValueOnce(mockProfile);
    vi.spyOn(profileApi, 'getDietaryTaxonomy').mockResolvedValueOnce(mockTaxonomy);
    const updatedRestrictions: DietaryRestrictionItem[] = [
      { code: 'CELIAC', name: 'Sin TACC / Celíaco', category: 'DIETARY_LIFESTYLE' },
    ];
    vi.spyOn(profileApi, 'updateDietaryRestrictions').mockResolvedValueOnce(updatedRestrictions);

    const { result } = renderHook(() => useProfile());
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.updateDietaryRestrictions(['CELIAC']);
    });

    expect(result.current.profile?.dietaryRestrictions).toEqual(updatedRestrictions);
  });

  it('initiates email change, sets 60s cooldown and refreshes profile', async () => {
    vi.spyOn(profileApi, 'getProfile').mockResolvedValue(mockProfile);
    vi.spyOn(profileApi, 'getDietaryTaxonomy').mockResolvedValue(mockTaxonomy);
    vi.spyOn(profileApi, 'requestEmailChange').mockResolvedValueOnce({
      message: 'Enviado',
      pendingEmail: 'new.valen@easyeat.com',
      expiresAt: '2026-10-26T12:00:00Z',
    });

    const { result } = renderHook(() => useProfile());
    await waitFor(() => expect(result.current.loading).toBe(false));

    vi.spyOn(profileApi, 'getProfile').mockResolvedValueOnce({
      ...mockProfile,
      pendingEmail: 'new.valen@easyeat.com',
      emailVerificationPending: true,
    });

    await act(async () => {
      await result.current.requestEmailChange('new.valen@easyeat.com');
    });

    expect(result.current.cooldownSeconds).toBe(60);
    expect(result.current.profile?.pendingEmail).toBe('new.valen@easyeat.com');
  });

  it('handles 429 response on resend verification by capturing retryAfterSeconds', async () => {
    vi.spyOn(profileApi, 'getProfile').mockResolvedValueOnce(mockProfile);
    vi.spyOn(profileApi, 'getDietaryTaxonomy').mockResolvedValueOnce(mockTaxonomy);
    const error429 = {
      response: {
        status: 429,
        data: { retryAfterSeconds: 45, detail: 'Cooldown activo' },
      },
    };
    vi.spyOn(profileApi, 'resendVerificationEmail').mockRejectedValueOnce(error429);

    const { result } = renderHook(() => useProfile());
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      try {
        await result.current.resendVerification();
      } catch (e) {
        // expected
      }
    });

    expect(result.current.cooldownSeconds).toBe(45);
    expect(result.current.error).toBe('Cooldown activo');
  });
});
