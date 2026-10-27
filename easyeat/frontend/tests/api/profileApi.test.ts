import { describe, it, expect, beforeEach, vi } from 'vitest';
import { profileApi } from '@/api/profileApi';
import { apiClient } from '@/api/apiClient';
import type {
  ProfileResponse,
  PersonalDataRequest,
  EmailChangeRequestDto,
  EmailChangeInitiatedResponse,
  EmailConfirmationRequest,
  EmailConfirmedResponse,
  DietaryRestrictionItem,
} from '@/types/profile';

describe('profileApi (T020, COM-1.4.1 & COM-1.4.3)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('calls GET /profile/me and returns ProfileResponse', async () => {
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

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({ data: mockProfile });
    const result = await profileApi.getProfile();

    expect(getSpy).toHaveBeenCalledWith('/profile/me');
    expect(result).toEqual(mockProfile);
  });

  it('calls PUT /profile/personal-data and returns updated ProfileResponse', async () => {
    const request: PersonalDataRequest = {
      firstName: 'Agustin',
      lastName: 'Fraga',
      phone: '+5491198765432',
    };
    const mockProfile: ProfileResponse = {
      id: 'uuid-1',
      firstName: 'Agustin',
      lastName: 'Fraga',
      fullName: 'Agustin Fraga',
      phone: '+5491198765432',
      email: 'valen@easyeat.com',
      pendingEmail: null,
      emailVerificationPending: false,
      dietaryRestrictions: [],
    };

    const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValueOnce({ data: mockProfile });
    const result = await profileApi.updatePersonalData(request);

    expect(putSpy).toHaveBeenCalledWith('/profile/personal-data', request);
    expect(result).toEqual(mockProfile);
  });

  it('calls POST /profile/email-change/request and returns initiated response', async () => {
    const request: EmailChangeRequestDto = { newEmail: 'new.valen@easyeat.com' };
    const mockResponse: EmailChangeInitiatedResponse = {
      message: 'Enlace enviado',
      pendingEmail: 'new.valen@easyeat.com',
      expiresAt: '2026-10-26T12:00:00Z',
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ data: mockResponse });
    const result = await profileApi.requestEmailChange(request);

    expect(postSpy).toHaveBeenCalledWith('/profile/email-change/request', request);
    expect(result).toEqual(mockResponse);
  });

  it('calls POST /profile/email-change/resend and returns initiated response', async () => {
    const mockResponse: EmailChangeInitiatedResponse = {
      message: 'Reenviado',
      pendingEmail: 'new.valen@easyeat.com',
      expiresAt: '2026-10-26T12:00:00Z',
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ data: mockResponse });
    const result = await profileApi.resendVerificationEmail();

    expect(postSpy).toHaveBeenCalledWith('/profile/email-change/resend');
    expect(result).toEqual(mockResponse);
  });

  it('calls DELETE /profile/email-change/pending', async () => {
    const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValueOnce({ data: undefined });
    await profileApi.cancelPendingEmailChange();

    expect(deleteSpy).toHaveBeenCalledWith('/profile/email-change/pending');
  });

  it('calls POST /profile/email-change/confirm and returns confirmed response', async () => {
    const request: EmailConfirmationRequest = { token: 'raw-token-123' };
    const mockResponse: EmailConfirmedResponse = {
      message: 'Email verificado exitosamente',
      activeEmail: 'new.valen@easyeat.com',
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ data: mockResponse });
    const result = await profileApi.confirmEmailChange(request);

    expect(postSpy).toHaveBeenCalledWith('/profile/email-change/confirm', request);
    expect(result).toEqual(mockResponse);
  });

  it('calls GET /dietary-restrictions and returns taxonomy list', async () => {
    const mockTaxonomy: DietaryRestrictionItem[] = [
      { code: 'CELIAC', name: 'Sin TACC', category: 'DIETARY_LIFESTYLE' },
      { code: 'PEANUTS', name: 'Maní', category: 'COMMON_ALLERGEN' },
    ];

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({ data: mockTaxonomy });
    const result = await profileApi.getDietaryTaxonomy();

    expect(getSpy).toHaveBeenCalledWith('/dietary-restrictions');
    expect(result).toEqual(mockTaxonomy);
  });

  it('calls PUT /profile/dietary-restrictions and returns updated restrictions', async () => {
    const codes = ['CELIAC', 'PEANUTS'];
    const mockUpdated: DietaryRestrictionItem[] = [
      { code: 'CELIAC', name: 'Sin TACC', category: 'DIETARY_LIFESTYLE' },
      { code: 'PEANUTS', name: 'Maní', category: 'COMMON_ALLERGEN' },
    ];

    const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValueOnce({ data: mockUpdated });
    const result = await profileApi.updateDietaryRestrictions(codes);

    expect(putSpy).toHaveBeenCalledWith('/profile/dietary-restrictions', { restrictionCodes: codes });
    expect(result).toEqual(mockUpdated);
  });
});
