import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginForm } from '@/components/auth/LoginForm';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import { authApi } from '@/api/authApi';

function createMockJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.mock-signature`;
}

const LoginWithAuthContext: React.FC = () => {
  const { user, isAuthenticated, setToken } = useAuth();

  const handleLogin = async (credentials: { email: string; password: string }) => {
    const data = await authApi.login(credentials);
    setToken(data.access_token);
  };

  return (
    <div>
      <div data-testid="auth-status">{isAuthenticated ? 'authenticated' : 'unauthenticated'}</div>
      {user && <div data-testid="user-role">{user.role}</div>}
      {user && <div data-testid="user-email">{user.email}</div>}
      {user && <div data-testid="user-tenant">{user.tenantId}</div>}
      <LoginForm onSubmit={handleLogin} />
    </div>
  );
};

describe('LoginForm Component', () => {
  const mockOnSubmit = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders email and password inputs and submit button', () => {
    render(<LoginForm onSubmit={mockOnSubmit} />);

    expect(screen.getByLabelText(/correo electrónico/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/contraseña/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /ingresar/i })).toBeInTheDocument();
  });

  it('displays validation errors when submitting empty fields (FR-002)', async () => {
    const user = userEvent.setup();
    render(<LoginForm onSubmit={mockOnSubmit} />);

    const submitBtn = screen.getByRole('button', { name: /ingresar/i });
    await user.click(submitBtn);

    expect(screen.getByText(/el correo electrónico es obligatorio/i)).toBeInTheDocument();
    expect(screen.getByText(/la contraseña es obligatoria/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('displays validation error on invalid email syntax (FR-003)', async () => {
    const user = userEvent.setup();
    render(<LoginForm onSubmit={mockOnSubmit} />);

    const emailInput = screen.getByLabelText(/correo electrónico/i);
    const passwordInput = screen.getByLabelText(/contraseña/i);
    const submitBtn = screen.getByRole('button', { name: /ingresar/i });

    await user.type(emailInput, 'correo-invalido');
    await user.type(passwordInput, 'Secret123');
    await user.click(submitBtn);

    expect(screen.getByText(/formato de correo electrónico inválido/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('normalizes email (strips whitespace and converts to lowercase) before submitting (FR-004)', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockResolvedValueOnce(undefined);
    render(<LoginForm onSubmit={mockOnSubmit} />);

    const emailInput = screen.getByLabelText(/correo electrónico/i);
    const passwordInput = screen.getByLabelText(/contraseña/i);
    const submitBtn = screen.getByRole('button', { name: /ingresar/i });

    await user.type(emailInput, '  Carlos.Chef@ComedorCentral.Com  ');
    await user.type(passwordInput, 'Password123');
    await user.click(submitBtn);

    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledWith({
        email: 'carlos.chef@comedorcentral.com',
        password: 'Password123',
      });
    });
  });

  it('disables submit button and prevents duplicate submissions while request is in flight (FR-005, SC-007)', async () => {
    const user = userEvent.setup();
    let resolveSubmit: () => void;
    const submitPromise = new Promise<void>((resolve) => {
      resolveSubmit = resolve;
    });
    mockOnSubmit.mockReturnValue(submitPromise);

    render(<LoginForm onSubmit={mockOnSubmit} />);

    const emailInput = screen.getByLabelText(/correo electrónico/i);
    const passwordInput = screen.getByLabelText(/contraseña/i);
    const submitBtn = screen.getByRole('button', { name: /ingresar/i });

    await user.type(emailInput, 'maria@laesquina.com');
    await user.type(passwordInput, 'Password123');

    // Click twice rapidly
    await user.click(submitBtn);
    await user.click(submitBtn);

    expect(mockOnSubmit).toHaveBeenCalledTimes(1);
    expect(submitBtn).toBeDisabled();

    // Finish request
    resolveSubmit!();
    await waitFor(() => {
      expect(submitBtn).not.toBeDisabled();
    });
  });

  it('displays generic anti-enumeration error on 401 unauthorized (FR-006)', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockRejectedValueOnce({
      response: {
        status: 401,
        data: {
          detail: 'Credenciales inválidas',
        },
      },
    });

    render(<LoginForm onSubmit={mockOnSubmit} />);

    await user.type(screen.getByLabelText(/correo electrónico/i), 'user@test.com');
    await user.type(screen.getByLabelText(/contraseña/i), 'WrongPass');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    await waitFor(() => {
      expect(screen.getByText(/credenciales inválidas/i)).toBeInTheDocument();
    });
  });

  it('displays network failure error when server is unreachable (FR-018)', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockRejectedValueOnce(new Error('Network Error'));

    render(<LoginForm onSubmit={mockOnSubmit} />);

    await user.type(screen.getByLabelText(/correo electrónico/i), 'user@test.com');
    await user.type(screen.getByLabelText(/contraseña/i), 'Password123');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    await waitFor(() => {
      expect(screen.getByText(/error de conexión/i)).toBeInTheDocument();
    });
  });

  it('submits credentials for maria.encargada@laesquina.com, receives valid token, and initializes AuthContext (T019)', async () => {
    sessionStorage.clear();
    const user = userEvent.setup();
    const mockToken = createMockJwt({
      sub: 'maria-uuid-123',
      email: 'maria.encargada@laesquina.com',
      role: 'ENCARGADO',
      tenant_id: 'la-esquina-tenant-999',
      token_version: 1,
      exp: Math.floor(Date.now() / 1000) + 28800,
    });

    vi.spyOn(authApi, 'login').mockResolvedValueOnce({
      access_token: mockToken,
      token_type: 'Bearer',
      expires_in: 28800,
    });

    render(
      <AuthProvider>
        <LoginWithAuthContext />
      </AuthProvider>
    );

    expect(screen.getByTestId('auth-status')).toHaveTextContent('unauthenticated');

    const emailInput = screen.getByLabelText(/correo electrónico/i);
    const passwordInput = screen.getByLabelText(/contraseña/i);
    const submitBtn = screen.getByRole('button', { name: /ingresar/i });

    await user.type(emailInput, '  Maria.Encargada@LaEsquina.com  ');
    await user.type(passwordInput, 'EncargadoPassword123');
    await user.click(submitBtn);

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith({
        email: 'maria.encargada@laesquina.com',
        password: 'EncargadoPassword123',
      });
      expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated');
      expect(screen.getByTestId('user-role')).toHaveTextContent('ENCARGADO');
      expect(screen.getByTestId('user-email')).toHaveTextContent('maria.encargada@laesquina.com');
      expect(screen.getByTestId('user-tenant')).toHaveTextContent('la-esquina-tenant-999');
      expect(sessionStorage.getItem('access_token')).toBe(mockToken);
    });
  });
});

