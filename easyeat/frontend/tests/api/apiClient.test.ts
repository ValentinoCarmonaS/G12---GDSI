import { describe, it, expect, beforeEach, vi } from 'vitest';
import { apiClient } from '../../src/api/apiClient';
import type { InternalAxiosRequestConfig } from 'axios';

describe('apiClient', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('configures default baseURL and standard JSON headers', () => {
    expect(apiClient.defaults.baseURL).toBe('/api/v1');
    expect(apiClient.defaults.headers['Content-Type']).toBe('application/json');
  });

  it('injects Bearer token into Authorization header when access_token exists in sessionStorage', async () => {
    sessionStorage.setItem('access_token', 'mock-jwt-token-12345');

    // Retrieve request interceptor handler from apiClient
    const handlers = (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }>;
    }).handlers;

    expect(handlers.length).toBeGreaterThan(0);

    const initialConfig = {
      headers: {} as Record<string, string>,
    } as unknown as InternalAxiosRequestConfig;

    const modifiedConfig = handlers[0].fulfilled(initialConfig);

    expect(modifiedConfig.headers.Authorization).toBe('Bearer mock-jwt-token-12345');
  });

  it('does not inject Authorization header when access_token is absent in sessionStorage', async () => {
    sessionStorage.removeItem('access_token');

    const handlers = (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }>;
    }).handlers;

    const initialConfig = {
      headers: {} as Record<string, string>,
    } as unknown as InternalAxiosRequestConfig;

    const modifiedConfig = handlers[0].fulfilled(initialConfig);

    expect(modifiedConfig.headers.Authorization).toBeUndefined();
  });
});
