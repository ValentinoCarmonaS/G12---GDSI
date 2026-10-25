import { describe, it, expect, beforeEach, vi } from 'vitest';
import { authApi } from '@/api/authApi';
import { apiClient } from '@/api/apiClient';
import type { LoginCredentials, LoginResponse } from '@/types/auth';

describe('authApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('calls POST /auth/login with credentials and returns LoginResponse (T018)', async () => {
    const mockCredentials: LoginCredentials = {
      email: 'maria.encargada@laesquina.com',
      password: 'EncargadoPassword123',
    };

    const mockResponse: LoginResponse = {
      access_token: 'mock-jwt-token-encargado',
      token_type: 'Bearer',
      expires_in: 28800,
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({
      data: mockResponse,
    });

    const result = await authApi.login(mockCredentials);

    expect(postSpy).toHaveBeenCalledWith('/auth/login', mockCredentials);
    expect(result).toEqual(mockResponse);
  });

  it('calls POST /auth/logout (FR-012)', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ data: null });
    await authApi.logout();
    expect(postSpy).toHaveBeenCalledWith('/auth/logout');
  });

  it('calls GET /auth/me for session liveness check (FR-010)', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({ data: null });
    await authApi.me();
    expect(getSpy).toHaveBeenCalledWith('/auth/me');
  });

  it('calls POST /auth/first-login-password-change (FR-003, FR-030)', async () => {
    const payload = {
      currentPassword: 'TempPassword123!',
      newPassword: 'PermanentSecurePassword456!',
      confirmNewPassword: 'PermanentSecurePassword456!',
    };
    const mockResponse = {
      message: 'Password updated successfully. Account is now active.',
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ data: mockResponse });
    const result = await authApi.changeFirstLoginPassword(payload);

    expect(postSpy).toHaveBeenCalledWith('/auth/first-login-password-change', payload);
    expect(result).toEqual(mockResponse);
  });
});
