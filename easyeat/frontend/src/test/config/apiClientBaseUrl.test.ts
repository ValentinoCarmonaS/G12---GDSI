import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

describe('apiClient baseURL resolution (T020, AS-19, FR-020)', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('adopta import.meta.env.VITE_API_BASE_URL cuando está definida en el entorno', async () => {
    const customUrl = 'https://easyeat-backend-test.onrender.com/api/v1';
    vi.stubEnv('VITE_API_BASE_URL', customUrl);

    const { apiClient } = await import('../../api/apiClient');
    expect(apiClient.defaults.baseURL).toBe(customUrl);
  });

  it('aplica fallback a /api/v1 cuando VITE_API_BASE_URL no está definida o está vacía', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '');

    const { apiClient } = await import('../../api/apiClient');
    expect(apiClient.defaults.baseURL).toBe('/api/v1');
  });
});
