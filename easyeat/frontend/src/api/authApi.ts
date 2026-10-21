import { apiClient } from './apiClient';
import { LoginCredentials, LoginResponse } from '../types/auth';

export const authApi = {
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
