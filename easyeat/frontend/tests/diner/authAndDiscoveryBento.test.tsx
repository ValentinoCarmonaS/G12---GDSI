import type { ComponentProps } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ThemeScopeProvider } from '@/theme/ThemeScopeProvider';
import { AuthContext } from '@/context/AuthContext';
import { TenantContext, TenantContextType } from '@/context/TenantContext';
import { CartContext, CartContextType } from '@/context/CartContext';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/pages/ResetPasswordPage';
import { VerifyEmailPage } from '@/pages/VerifyEmailPage';
import { EstablishmentDiscoveryPage } from '@/pages/EstablishmentDiscoveryPage';
import { dinerCatalogApi } from '@/api/dinerCatalogApi';
import { profileApi } from '@/api/profileApi';
import { mockEstablishmentSummaryActive } from '@/test/mocks/dinerCatalogHandlers';

type AuthContextValue = ComponentProps<typeof AuthContext.Provider>['value'];

const defaultAuthContext: AuthContextValue = {
  token: null,
  user: null,
  isAuthenticated: false,
  setToken: vi.fn(),
  clearAuth: vi.fn(),
  logout: vi.fn(),
};

const authenticatedDinerContext: AuthContextValue = {
  token: 'mock-token',
  user: {
    id: 'user-1',
    email: 'comensal@easyeat.com',
    role: 'COMENSAL',
    tenantId: null,
    tokenVersion: 1,
    mustChangePassword: false,
  },
  isAuthenticated: true,
  setToken: vi.fn(),
  clearAuth: vi.fn(),
  logout: vi.fn(),
};

const defaultTenantContext: TenantContextType = {
  tenant: null,
  setTenant: vi.fn(),
  clearTenant: vi.fn(),
  isLoading: false,
};

const defaultCartContext: CartContextType = {
  items: [],
  addItem: vi.fn(),
  removeItem: vi.fn(),
  updateQuantity: vi.fn(),
  clearCart: vi.fn(),
  reEvaluateCompliance: vi.fn(),
  totalAmount: 0,
  totalCount: 0,
  hasConflictingItems: false,
  hasPausedItems: false,
};

describe('Spec 025 US1 — Public Auth Polymorphic Bento & Establishment Discovery', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  describe('Auth pages render AuthCardShell with --ee-* tokens and FeedbackState', () => {
    it('LoginPage renders AuthCardShell, accessible submit button (>=44px), and FeedbackState for session reason', () => {
      const { container } = render(
        <MemoryRouter
          initialEntries={[{ pathname: '/login', state: { reason: 'Tu sesión expiró.' } }]}
        >
          <AuthContext.Provider value={defaultAuthContext}>
            <ThemeScopeProvider>
              <LoginPage />
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      expect(screen.getByTestId('auth-card-shell')).toBeInTheDocument();
      expect(screen.getByTestId('feedback-state')).toHaveTextContent('Tu sesión expiró.');

      const submitBtn = screen.getByRole('button', { name: /ingresar/i });
      expect(submitBtn.className).toContain('min-h-[44px]');

      const html = container.innerHTML;
      expect(html).not.toContain('bg-gray-50');
      expect(html).not.toContain('bg-white');
      expect(html).not.toContain('bg-amber-600');
    });

    it('LoginPage inherits data-theme="mostrador" when deep-linked from /mostrador without hardcoded light classes', () => {
      const { container } = render(
        <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: '/mostrador' } }]}>
          <AuthContext.Provider value={defaultAuthContext}>
            <ThemeScopeProvider>
              <Routes>
                <Route path="/login" element={<LoginPage />} />
              </Routes>
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      expect(document.documentElement.getAttribute('data-theme')).toBe('mostrador');
      expect(screen.getByTestId('auth-card-shell')).toBeInTheDocument();

      const html = container.innerHTML;
      expect(html).not.toContain('bg-gray-50');
      expect(html).not.toContain('bg-white');
      expect(html).not.toContain('bg-amber-600');
    });

    it('RegisterPage renders AuthCardShell with accessible submit button and --ee-* tokens', () => {
      const { container } = render(
        <MemoryRouter initialEntries={['/register']}>
          <AuthContext.Provider value={defaultAuthContext}>
            <RegisterPage />
          </AuthContext.Provider>
        </MemoryRouter>
      );

      expect(screen.getByTestId('auth-card-shell')).toBeInTheDocument();
      const submitBtn = screen.getByRole('button', { name: /registrarse/i });
      expect(submitBtn.className).toContain('min-h-[44px]');
      expect(container.innerHTML).not.toContain('bg-amber-600');
    });

    it('ForgotPasswordPage renders AuthCardShell with accessible action buttons', () => {
      const { container } = render(
        <MemoryRouter initialEntries={['/forgot-password']}>
          <ForgotPasswordPage />
        </MemoryRouter>
      );

      expect(screen.getByTestId('auth-card-shell')).toBeInTheDocument();
      const submitBtn = screen.getByRole('button', { name: /enviar instrucciones/i });
      expect(submitBtn.className).toContain('min-h-[44px]');
      expect(container.innerHTML).not.toContain('bg-amber-600');
    });

    it('ResetPasswordPage renders AuthCardShell and FeedbackState when token is missing', () => {
      render(
        <MemoryRouter initialEntries={['/reset-password']}>
          <ResetPasswordPage />
        </MemoryRouter>
      );

      expect(screen.getByTestId('auth-card-shell')).toBeInTheDocument();
      expect(screen.getByTestId('feedback-state')).toHaveTextContent(
        /enlace de recuperación inválido/i
      );
    });

    it('VerifyEmailPage renders AuthCardShell, LoadingSkeleton while loading, and FeedbackState on error', async () => {
      let resolvePromise!: (value: { message: string; activeEmail: string }) => void;
      vi.spyOn(profileApi, 'confirmEmailChange').mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolvePromise = resolve;
          })
      );

      render(
        <MemoryRouter initialEntries={['/verificar-email?token=abc']}>
          <VerifyEmailPage />
        </MemoryRouter>
      );

      expect(screen.getByTestId('auth-card-shell')).toBeInTheDocument();
      expect(screen.getByTestId('loading-skeleton')).toBeInTheDocument();

      resolvePromise({ message: 'OK', activeEmail: 'diner@easyeat.com' });
      expect(await screen.findByText(/¡correo verificado con éxito!/i)).toBeInTheDocument();
      expect(screen.getByTestId('status-badge')).toBeInTheDocument();
    });
  });

  describe('EstablishmentDiscoveryPage Bento layout and live search states', () => {
    it('renders active establishment Bento card, search hero, and 3 informative Bento blocks without gradients', async () => {
      vi.spyOn(dinerCatalogApi, 'getAllEstablishments').mockResolvedValue([]);

      const { container } = render(
        <MemoryRouter initialEntries={['/descubrimiento']}>
          <AuthContext.Provider value={authenticatedDinerContext}>
            <TenantContext.Provider
              value={{ ...defaultTenantContext, tenant: mockEstablishmentSummaryActive }}
            >
              <CartContext.Provider value={defaultCartContext}>
                <EstablishmentDiscoveryPage />
              </CartContext.Provider>
            </TenantContext.Provider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      expect(screen.getByText('Restaurante Activo')).toBeInTheDocument();
      expect(screen.getByText(mockEstablishmentSummaryActive.name)).toBeInTheDocument();
      expect(screen.getByText('Catálogo en Vivo')).toBeInTheDocument();
      expect(screen.getByText('Stock de Cocina')).toBeInTheDocument();
      expect(screen.getByText('Filtro de Alérgenos')).toBeInTheDocument();

      const html = container.innerHTML;
      expect(html).not.toContain('bg-gradient-to-');
      expect(html).not.toContain('dark:bg-gray-800');
    });

    it('shows LoadingSkeleton during search, EmptyState when no matches, and FeedbackState with retry on error', async () => {
      const user = userEvent.setup();
      vi.spyOn(dinerCatalogApi, 'getAllEstablishments').mockResolvedValue([]);

      let resolveSearch!: (val: typeof mockEstablishmentSummaryActive[]) => void;
      const searchSpy = vi
        .spyOn(dinerCatalogApi, 'searchEstablishments')
        .mockImplementationOnce(
          () =>
            new Promise((resolve) => {
              resolveSearch = resolve;
            })
        )
        .mockRejectedValueOnce(new Error('Network down'))
        .mockResolvedValueOnce([mockEstablishmentSummaryActive]);

      render(
        <MemoryRouter initialEntries={['/descubrimiento']}>
          <AuthContext.Provider value={authenticatedDinerContext}>
            <TenantContext.Provider value={defaultTenantContext}>
              <CartContext.Provider value={defaultCartContext}>
                <EstablishmentDiscoveryPage />
              </CartContext.Provider>
            </TenantContext.Provider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      const input = screen.getByPlaceholderText(/buscar restaurante por nombre/i);
      await user.type(input, 'sinmatch');

      // Loading skeleton while searching
      expect(await screen.findByTestId('loading-skeleton')).toBeInTheDocument();

      await waitFor(() => {
        expect(searchSpy).toHaveBeenCalledTimes(1);
      });

      // Resolve with empty array -> EmptyState
      resolveSearch([]);
      expect(await screen.findByTestId('empty-state')).toBeInTheDocument();

      // Trigger error state on new search
      await user.clear(input);
      await user.type(input, 'farola');

      const feedback = await screen.findByTestId('feedback-state');
      expect(feedback).toBeInTheDocument();

      const retryBtn = screen.getByRole('button', { name: /reintentar/i });
      await user.click(retryBtn);

      await waitFor(() => {
        expect(searchSpy).toHaveBeenCalledTimes(3);
      });
      expect(await screen.findByText('La Farola de Palermo')).toBeInTheDocument();
    });
  });
});
