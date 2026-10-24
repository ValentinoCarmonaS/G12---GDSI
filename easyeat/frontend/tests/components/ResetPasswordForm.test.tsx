import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ResetPasswordForm } from '@/components/auth/ResetPasswordForm';

describe('ResetPasswordForm Component', () => {
  const mockOnSubmit = vi.fn();
  const validToken = 'valid-reset-token-12345';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders new password and confirmation inputs and submit button (FR-024)', () => {
    render(<ResetPasswordForm token={validToken} onSubmit={mockOnSubmit} />);

    expect(screen.getByLabelText(/^nueva contraseña/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirmar contraseña/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /restablecer contraseña/i })).toBeInTheDocument();
  });

  it('displays validation errors when fields are empty', async () => {
    const user = userEvent.setup();
    render(<ResetPasswordForm token={validToken} onSubmit={mockOnSubmit} />);

    await user.click(screen.getByRole('button', { name: /restablecer contraseña/i }));

    expect(screen.getByText(/la nueva contraseña es obligatoria/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('displays error when passwords do not match', async () => {
    const user = userEvent.setup();
    render(<ResetPasswordForm token={validToken} onSubmit={mockOnSubmit} />);

    await user.type(screen.getByLabelText(/^nueva contraseña/i), 'SecurePass1');
    await user.type(screen.getByLabelText(/confirmar contraseña/i), 'DifferentPass2');
    await user.click(screen.getByRole('button', { name: /restablecer contraseña/i }));

    expect(screen.getByText(/las contraseñas no coinciden/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('displays error when password does not meet complexity requirements (FR-003)', async () => {
    const user = userEvent.setup();
    render(<ResetPasswordForm token={validToken} onSubmit={mockOnSubmit} />);

    await user.type(screen.getByLabelText(/^nueva contraseña/i), 'weak');
    await user.type(screen.getByLabelText(/confirmar contraseña/i), 'weak');
    await user.click(screen.getByRole('button', { name: /restablecer contraseña/i }));

    expect(
      screen.getByText(/la contraseña no cumple con los requisitos mínimos de seguridad/i)
    ).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('submits valid new password to onSubmit handler (FR-024)', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockResolvedValueOnce(undefined);

    render(<ResetPasswordForm token={validToken} onSubmit={mockOnSubmit} />);

    await user.type(screen.getByLabelText(/^nueva contraseña/i), 'StrongNewPass1');
    await user.type(screen.getByLabelText(/confirmar contraseña/i), 'StrongNewPass1');
    await user.click(screen.getByRole('button', { name: /restablecer contraseña/i }));

    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledWith({
        token: validToken,
        new_password: 'StrongNewPass1',
      });
    });
  });

  it('displays error message when token is expired or consumed (FR-014)', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockRejectedValueOnce({
      response: {
        status: 400,
        data: {
          detail: 'El enlace ha expirado o ya ha sido utilizado',
        },
      },
    });

    render(<ResetPasswordForm token={validToken} onSubmit={mockOnSubmit} />);

    await user.type(screen.getByLabelText(/^nueva contraseña/i), 'StrongNewPass1');
    await user.type(screen.getByLabelText(/confirmar contraseña/i), 'StrongNewPass1');
    await user.click(screen.getByRole('button', { name: /restablecer contraseña/i }));

    await waitFor(() => {
      expect(
        screen.getByText(/el enlace ha expirado o ya ha sido utilizado/i)
      ).toBeInTheDocument();
    });
  });
});
