import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from '../../router/AppRoutes';
import { useAuth } from '../../hooks/useAuth';

vi.mock('../../hooks/useAuth', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../../pages/CheckoutPage', () => ({
  CheckoutPage: () => <div data-testid="checkout-page-mock">Checkout Page View</div>,
}));

vi.mock('../../pages/LoginPage', () => ({
  LoginPage: () => <div data-testid="login-page-mock">Login Page View</div>,
}));

describe('AppRoutes - /checkout Route Guard (T042 / US4)', () => {
  it('redirects unauthenticated user to /login when accessing /checkout', () => {
    vi.mocked(useAuth).mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      login: vi.fn(),
      logout: vi.fn(),
      refreshAuth: vi.fn(),
    } as any);

    render(
      <MemoryRouter initialEntries={['/checkout']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(screen.getByTestId('login-page-mock')).toBeInTheDocument();
    expect(screen.queryByTestId('checkout-page-mock')).not.toBeInTheDocument();
  });

  it('allows access to /checkout for authenticated user with ROLE_COMENSAL', () => {
    vi.mocked(useAuth).mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'user-1',
        email: 'diner@test.com',
        role: 'COMENSAL',
        status: 'ACTIVE',
        mustChangePassword: false,
      },
      token: 'jwt-token',
      login: vi.fn(),
      logout: vi.fn(),
      refreshAuth: vi.fn(),
    } as any);

    render(
      <MemoryRouter initialEntries={['/checkout']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(screen.getByTestId('checkout-page-mock')).toBeInTheDocument();
  });

  it('blocks access to /checkout for staff role CHEF', () => {
    vi.mocked(useAuth).mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'user-chef',
        email: 'chef@test.com',
        role: 'CHEF',
        status: 'ACTIVE',
        mustChangePassword: false,
      },
      token: 'jwt-token',
      login: vi.fn(),
      logout: vi.fn(),
      refreshAuth: vi.fn(),
    } as any);

    render(
      <MemoryRouter initialEntries={['/checkout']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(screen.queryByTestId('checkout-page-mock')).not.toBeInTheDocument();
  });
});
