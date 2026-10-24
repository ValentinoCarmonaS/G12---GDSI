import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setupSessionInterceptor } from '@/api/apiClient';
import axios from 'axios';

describe('Session Lifecycle and 401 Interception (FR-014, FR-015)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it('evicts token from sessionStorage on 401 response and dispatches session-expired event', async () => {
    sessionStorage.setItem('access_token', 'valid-looking-token');

    const mockAxiosInstance = axios.create();
    const mockRedirect = vi.fn();
    const eventListener = vi.fn();

    window.addEventListener('easyeat:session-expired', eventListener);

    setupSessionInterceptor(mockAxiosInstance, mockRedirect);

    // Simulate 401 response from an intercepted call
    try {
      // Trigger interceptor rejection logic directly
      const interceptors = (mockAxiosInstance.interceptors.response as unknown as {
        handlers: Array<{ rejected: (error: unknown) => Promise<never> }>;
      }).handlers;

      const errorHandler = interceptors[interceptors.length - 1].rejected;
      await errorHandler({
        response: { status: 401, data: { detail: 'Token version mismatch' } },
        config: {},
      });
    } catch {
      // Expected to reject
    }

    expect(sessionStorage.getItem('access_token')).toBeNull();
    expect(mockRedirect).toHaveBeenCalledWith('/login', {
      state: { reason: 'Tu sesión ha expirado. Por favor, iniciá sesión nuevamente.' },
    });
    expect(eventListener).toHaveBeenCalled();

    window.removeEventListener('easyeat:session-expired', eventListener);
  });
});
