import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { setupSessionInterceptor } from '@/api/apiClient';
import { LoginPage } from '@/pages/LoginPage';
import { AuthProvider } from '@/context/AuthContext';
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

  it('displays session expiration alert banner on /login when redirected after 401 eviction (T039)', () => {
    render(
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { reason: 'Tu sesión ha expirado. Por favor, iniciá sesión nuevamente.' } }]}>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>
    );

    expect(screen.getByRole('alert')).toHaveTextContent(/tu sesión ha expirado/i);
  });

  it('displays session expiration alert banner on /login when session_expired_reason is in sessionStorage (T039)', () => {
    sessionStorage.setItem('session_expired_reason', 'Tu sesión ha expirado. Por favor, iniciá sesión nuevamente.');

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>
    );

    expect(screen.getByRole('alert')).toHaveTextContent(/tu sesión ha expirado/i);
    // Verified that storage is cleared after consumption
    expect(sessionStorage.getItem('session_expired_reason')).toBeNull();
  });

  it('does not trigger session eviction or redirect when 401 is returned from /auth/login (bad credentials)', async () => {
    sessionStorage.setItem('access_token', 'initial-token');
    const mockAxiosInstance = axios.create();
    const mockRedirect = vi.fn();
    setupSessionInterceptor(mockAxiosInstance, mockRedirect);

    try {
      const interceptors = (mockAxiosInstance.interceptors.response as unknown as {
        handlers: Array<{ rejected: (error: unknown) => Promise<never> }>;
      }).handlers;
      const errorHandler = interceptors[interceptors.length - 1].rejected;
      await errorHandler({
        response: { status: 401, data: { detail: 'The provided email or password is incorrect.' } },
        config: { url: '/api/v1/auth/login' },
      });
    } catch {
      // Expected to reject to let LoginForm handle it
    }

    expect(sessionStorage.getItem('access_token')).toBe('initial-token');
    expect(mockRedirect).not.toHaveBeenCalled();
  });
});
