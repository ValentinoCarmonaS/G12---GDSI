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
};

export default authApi;
