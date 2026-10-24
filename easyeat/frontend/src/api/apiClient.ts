import axios, { AxiosInstance } from 'axios';

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor to inject bearer token from sessionStorage if present
apiClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('access_token');
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * Attaches a 401 response interceptor to an Axios instance for session eviction (FR-014, FR-015).
 */
export function setupSessionInterceptor(
  instance: AxiosInstance,
  onSessionExpiredRedirect?: (path: string, options?: { state?: { reason: string } }) => void
) {
  instance.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error?.response?.status === 401) {
        // Skip session eviction for login credential failures (handled by LoginForm)
        const requestUrl = error.config?.url || '';
        if (requestUrl.includes('/auth/login')) {
          return Promise.reject(error);
        }

        // Purge invalid/expired token from client storage
        sessionStorage.removeItem('access_token');

        // Dispatch application-wide session expiration event
        window.dispatchEvent(new CustomEvent('easyeat:session-expired'));

        const redirectFn =
          onSessionExpiredRedirect ||
          ((path, opts) => {
            if (opts?.state?.reason) {
              window.sessionStorage.setItem('session_expired_reason', opts.state.reason);
            }
            window.location.href = path;
          });

        redirectFn('/login', {
          state: { reason: 'Tu sesión ha expirado. Por favor, iniciá sesión nuevamente.' },
        });
      }
      return Promise.reject(error);
    }
  );
}

// Attach default session interceptor to the main apiClient instance
setupSessionInterceptor(apiClient);

export default apiClient;
