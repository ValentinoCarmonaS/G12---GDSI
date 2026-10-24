import { apiClient } from './apiClient';
import {
  LoginCredentials,
  LoginResponse,
  RegisterCredentials,
  RegisterResponse,
  PasswordResetRequest,
  PasswordResetConfirm,
  PasswordResetResponse,
} from '../types/auth';

export const authApi = {
  /**
   * Dispatches comensal registration request to the backend.
   */
  async register(credentials: RegisterCredentials): Promise<RegisterResponse> {
    const response = await apiClient.post<RegisterResponse>('/auth/register', credentials);
    return response.data;
  },

  /**
   * Requests a password reset link sent to the user's email.
   */
  async requestPasswordReset(data: PasswordResetRequest): Promise<PasswordResetResponse> {
    const response = await apiClient.post<PasswordResetResponse>('/auth/password-reset/request', data);
    return response.data;
  },

  /**
   * Confirms password reset with token and new password.
   */
  async confirmPasswordReset(data: PasswordResetConfirm): Promise<PasswordResetResponse> {
    const response = await apiClient.post<PasswordResetResponse>('/auth/password-reset/confirm', data);
    return response.data;
  },

  /**
   * Dispatches login authentication request to the backend.
   */
  async login(credentials: LoginCredentials): Promise<LoginResponse> {
    const response = await apiClient.post<LoginResponse>('/auth/login', credentials);
    return response.data;
  },

  /**
   * Dispatches explicit logout request to the backend.
   */
  async logout(): Promise<void> {
    await apiClient.post('/auth/logout');
  },

  /**
   * Validates the current bearer token against the backend (token_version check).
   * Used by workspace views on mount to detect concurrent session invalidation.
   * Throws on 401 (session expired), which is caught by the global interceptor.
   */
  async me(): Promise<void> {
    await apiClient.get('/auth/me');
  },
};

export default authApi;
