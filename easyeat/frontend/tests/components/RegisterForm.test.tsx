import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RegisterForm } from '@/components/auth/RegisterForm';

describe('RegisterForm Component', () => {
  const mockOnSubmit = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders email and password inputs and submit button', () => {
    render(<RegisterForm onSubmit={mockOnSubmit} />);

    expect(screen.getByLabelText(/correo electrónico/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^contraseña/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /registrarse/i })).toBeInTheDocument();
  });

  it('displays validation errors when submitting empty fields (FR-018)', async () => {
    const user = userEvent.setup();
    render(<RegisterForm onSubmit={mockOnSubmit} />);

    const submitBtn = screen.getByRole('button', { name: /registrarse/i });
    await user.click(submitBtn);

    expect(screen.getByText(/el correo electrónico es obligatorio/i)).toBeInTheDocument();
    expect(screen.getByText(/la contraseña es obligatoria/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('displays validation error on invalid email syntax (FR-018)', async () => {
    const user = userEvent.setup();
    render(<RegisterForm onSubmit={mockOnSubmit} />);

    const emailInput = screen.getByLabelText(/correo electrónico/i);
    const passwordInput = screen.getByLabelText(/^contraseña/i);
    const submitBtn = screen.getByRole('button', { name: /registrarse/i });

    await user.type(emailInput, 'correo-invalido');
    await user.type(passwordInput, 'ValidPass1');
    await user.click(submitBtn);

    expect(screen.getByText(/formato de correo electrónico inválido/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('visually highlights password complexity criteria in real-time (FR-003, FR-018)', async () => {
    const user = userEvent.setup();
    render(<RegisterForm onSubmit={mockOnSubmit} />);

    const passwordInput = screen.getByLabelText(/^contraseña/i);

    // Initial state: criteria indicators visible
    const minLengthCriteria = screen.getByText(/mínimo 8 caracteres/i);
    const uppercaseCriteria = screen.getByText(/al menos una mayúscula/i);
    const lowercaseCriteria = screen.getByText(/al menos una minúscula/i);
    const numberCriteria = screen.getByText(/al menos un número/i);

    expect(minLengthCriteria).toBeInTheDocument();
    expect(uppercaseCriteria).toBeInTheDocument();
    expect(lowercaseCriteria).toBeInTheDocument();
    expect(numberCriteria).toBeInTheDocument();

    // Type partial password: "pass" -> only lowercase met
    await user.type(passwordInput, 'pass');
    expect(lowercaseCriteria).toHaveAttribute('data-valid', 'true');
    expect(minLengthCriteria).toHaveAttribute('data-valid', 'false');
    expect(uppercaseCriteria).toHaveAttribute('data-valid', 'false');
    expect(numberCriteria).toHaveAttribute('data-valid', 'false');

    // Type uppercase and digit and extend to 8 chars: "passWORD1"
    await user.type(passwordInput, 'WORD1');
    expect(minLengthCriteria).toHaveAttribute('data-valid', 'true');
    expect(uppercaseCriteria).toHaveAttribute('data-valid', 'true');
    expect(lowercaseCriteria).toHaveAttribute('data-valid', 'true');
    expect(numberCriteria).toHaveAttribute('data-valid', 'true');
  });

  it('normalizes email (strips whitespace and converts to lowercase) before submitting (FR-002)', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockResolvedValueOnce(undefined);
    render(<RegisterForm onSubmit={mockOnSubmit} />);

    const emailInput = screen.getByLabelText(/correo electrónico/i);
    const passwordInput = screen.getByLabelText(/^contraseña/i);
    const submitBtn = screen.getByRole('button', { name: /registrarse/i });

    await user.type(emailInput, '  Comensal.Nuevo@EasyEat.Com  ');
    await user.type(passwordInput, 'SecurePass1');
    await user.click(submitBtn);

    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledWith({
        email: 'comensal.nuevo@easyeat.com',
        password: 'SecurePass1',
      });
    });
  });

  it('disables submit button and prevents duplicate submissions while request is in flight', async () => {
    const user = userEvent.setup();
    let resolveSubmit: () => void;
    const submitPromise = new Promise<void>((resolve) => {
      resolveSubmit = resolve;
    });
    mockOnSubmit.mockReturnValue(submitPromise);

    render(<RegisterForm onSubmit={mockOnSubmit} />);

    const emailInput = screen.getByLabelText(/correo electrónico/i);
    const passwordInput = screen.getByLabelText(/^contraseña/i);
    const submitBtn = screen.getByRole('button', { name: /registrarse/i });

    await user.type(emailInput, 'user@test.com');
    await user.type(passwordInput, 'SecurePass1');

    await user.click(submitBtn);
    await user.click(submitBtn);

    expect(mockOnSubmit).toHaveBeenCalledTimes(1);
    expect(submitBtn).toBeDisabled();

    resolveSubmit!();
    await waitFor(() => {
      expect(submitBtn).not.toBeDisabled();
    });
  });

  it('displays duplicate email error on 409 conflict (FR-001, US1 Scenario 2)', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockRejectedValueOnce({
      response: {
        status: 409,
        data: {
          detail: 'El correo electrónico ya se encuentra registrado',
        },
      },
    });

    render(<RegisterForm onSubmit={mockOnSubmit} />);

    await user.type(screen.getByLabelText(/correo electrónico/i), 'duplicado@test.com');
    await user.type(screen.getByLabelText(/^contraseña/i), 'SecurePass1');
    await user.click(screen.getByRole('button', { name: /registrarse/i }));

    await waitFor(() => {
      expect(screen.getByText(/el correo electrónico ya se encuentra registrado/i)).toBeInTheDocument();
    });
  });

  it('displays generic network failure error when server is unreachable', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockRejectedValueOnce(new Error('Network Error'));

    render(<RegisterForm onSubmit={mockOnSubmit} />);

    await user.type(screen.getByLabelText(/correo electrónico/i), 'user@test.com');
    await user.type(screen.getByLabelText(/^contraseña/i), 'SecurePass1');
    await user.click(screen.getByRole('button', { name: /registrarse/i }));

    await waitFor(() => {
      expect(screen.getByText(/error de conexión/i)).toBeInTheDocument();
    });
  });
});
