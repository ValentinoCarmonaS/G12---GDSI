import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ForgotPasswordForm } from '@/components/auth/ForgotPasswordForm';

describe('ForgotPasswordForm Component', () => {
  const mockOnSubmit = vi.fn();
  const mockOnCancel = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders email input, submit button, and cancel action (FR-022, FR-023)', () => {
    render(<ForgotPasswordForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />);

    expect(screen.getByLabelText(/correo electrónico/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /enviar instrucciones/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancelar/i })).toBeInTheDocument();
  });

  it('displays validation error when submitting empty email', async () => {
    const user = userEvent.setup();
    render(<ForgotPasswordForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />);

    await user.click(screen.getByRole('button', { name: /enviar instrucciones/i }));

    expect(screen.getByText(/el correo electrónico es obligatorio/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('displays validation error on invalid email syntax', async () => {
    const user = userEvent.setup();
    render(<ForgotPasswordForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />);

    await user.type(screen.getByLabelText(/correo electrónico/i), 'correo-no-valido');
    await user.click(screen.getByRole('button', { name: /enviar instrucciones/i }));

    expect(screen.getByText(/formato de correo electrónico inválido/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('submits normalized email and displays uniform anti-enumeration confirmation (FR-012, FR-022)', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockResolvedValueOnce(undefined);

    render(<ForgotPasswordForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />);

    await user.type(screen.getByLabelText(/correo electrónico/i), '  Comensal@Example.Com  ');
    await user.click(screen.getByRole('button', { name: /enviar instrucciones/i }));

    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledWith({ email: 'comensal@example.com' });
    });

    await waitFor(() => {
      expect(
        screen.getByText(/si el correo electrónico está registrado, recibirás las instrucciones/i)
      ).toBeInTheDocument();
    });
  });

  it('invokes onCancel when clicking cancel button (FR-023)', async () => {
    const user = userEvent.setup();
    render(<ForgotPasswordForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />);

    await user.click(screen.getByRole('button', { name: /cancelar/i }));

    expect(mockOnCancel).toHaveBeenCalledTimes(1);
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('disables submit button and shows loading while in flight', async () => {
    const user = userEvent.setup();
    let resolveSubmit: () => void;
    const submitPromise = new Promise<void>((resolve) => {
      resolveSubmit = resolve;
    });
    mockOnSubmit.mockReturnValue(submitPromise);

    render(<ForgotPasswordForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />);

    await user.type(screen.getByLabelText(/correo electrónico/i), 'test@example.com');
    const submitBtn = screen.getByRole('button', { name: /enviar instrucciones/i });

    await user.click(submitBtn);

    expect(submitBtn).toBeDisabled();
    expect(mockOnSubmit).toHaveBeenCalledTimes(1);

    resolveSubmit!();
    await waitFor(() => {
      expect(submitBtn).not.toBeDisabled();
    });
  });
});
