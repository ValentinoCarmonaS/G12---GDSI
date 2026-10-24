import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { LoginForm } from '@/components/auth/LoginForm';

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

  it('renders accessible navigation links to /register and /forgot-password (FR-021)', () => {
    render(
      <MemoryRouter>
        <LoginForm onSubmit={mockOnSubmit} />
      </MemoryRouter>
    );

    const forgotPasswordLink = screen.getByRole('link', { name: /olvidé mi contraseña/i });
    expect(forgotPasswordLink).toBeInTheDocument();
    expect(forgotPasswordLink).toHaveAttribute('href', '/forgot-password');

    const registerLink = screen.getByRole('link', { name: /registrate/i });
    expect(registerLink).toBeInTheDocument();
    expect(registerLink).toHaveAttribute('href', '/register');
  });
});
