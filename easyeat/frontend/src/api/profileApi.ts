import { apiClient } from './apiClient';
import type {
  ProfileResponse,
  PersonalDataRequest,
  EmailChangeRequestDto,
  EmailChangeInitiatedResponse,
  EmailConfirmationRequest,
  EmailConfirmedResponse,
} from '../types/profile';

export const profileApi = {
  /**
   * Retrieves the authenticated diner's profile details.
   */
  async getProfile(): Promise<ProfileResponse> {
    const response = await apiClient.get<ProfileResponse>('/profile/me');
    return response.data;
  },

  /**
   * Updates diner contact details (first name, last name, phone).
   */
  async updatePersonalData(request: PersonalDataRequest): Promise<ProfileResponse> {
    const response = await apiClient.put<ProfileResponse>('/profile/personal-data', request);
    return response.data;
  },

  /**
   * Initiates an asynchronous email address verification workflow.
   */
  async requestEmailChange(request: EmailChangeRequestDto): Promise<EmailChangeInitiatedResponse> {
    const response = await apiClient.post<EmailChangeInitiatedResponse>('/profile/email-change/request', request);
    return response.data;
  },

  /**
   * Redispatches the email verification link, subject to server cooldown.
   */
  async resendVerificationEmail(): Promise<EmailChangeInitiatedResponse> {
    const response = await apiClient.post<EmailChangeInitiatedResponse>('/profile/email-change/resend');
    return response.data;
  },

  /**
   * Cancels any active pending email change request for the diner.
   */
  async cancelPendingEmailChange(): Promise<void> {
    await apiClient.delete('/profile/email-change/pending');
  },

  /**
   * Public confirmation endpoint consuming the verification token.
   */
  async confirmEmailChange(request: EmailConfirmationRequest): Promise<EmailConfirmedResponse> {
    const response = await apiClient.post<EmailConfirmedResponse>('/profile/email-change/confirm', request);
    return response.data;
  },
};
