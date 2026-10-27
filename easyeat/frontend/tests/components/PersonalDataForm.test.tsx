import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PersonalDataForm } from '@/components/profile/PersonalDataForm';

describe('PersonalDataForm Component (T014, COM-1.4.1)', () => {
  const mockOnSubmit = vi.fn();
  const mockOnCancel = vi.fn();
  const mockOnRequestEmailChange = vi.fn();

  const sampleData = {
    firstName: 'Valentino',
    lastName: 'Carmona',
    phone: '+54 9 11 2345-6789',
    email: 'valentino@example.com',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders form inputs populated with initial diner data', () => {
    render(
      <PersonalDataForm
        initialData={sampleData}
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
        onRequestEmailChange={mockOnRequestEmailChange}
      />
    );

    expect(screen.getByLabelText(/nombre/i)).toHaveValue('Valentino');
    expect(screen.getByLabelText(/apellido/i)).toHaveValue('Carmona');
    expect(screen.getByLabelText(/teléfono/i)).toHaveValue('+54 9 11 2345-6789');
    expect(screen.getByDisplayValue('valentino@example.com')).toBeInTheDocument();
  });

  it('displays validation error when required fields are left blank', async () => {
    const user = userEvent.setup();
    render(
      <PersonalDataForm
        initialData={sampleData}
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
      />
    );

    const firstNameInput = screen.getByLabelText(/nombre/i);
    await user.clear(firstNameInput);

    const submitBtn = screen.getByRole('button', { name: /guardar cambios/i });
    await user.click(submitBtn);

    expect(await screen.findByText(/el nombre es obligatorio/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('displays validation error when names contain numbers or symbols', async () => {
    const user = userEvent.setup();
    render(
      <PersonalDataForm
        initialData={sampleData}
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
      />
    );

    const firstNameInput = screen.getByLabelText(/nombre/i);
    await user.clear(firstNameInput);
    await user.type(firstNameInput, 'Valen123');

    const submitBtn = screen.getByRole('button', { name: /guardar cambios/i });
    await user.click(submitBtn);

    expect(await screen.findByText(/solo puede contener letras/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('displays validation error when phone format is invalid', async () => {
    const user = userEvent.setup();
    render(
      <PersonalDataForm
        initialData={sampleData}
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
      />
    );

    const phoneInput = screen.getByLabelText(/teléfono/i);
    await user.clear(phoneInput);
    await user.type(phoneInput, '123');

    const submitBtn = screen.getByRole('button', { name: /guardar cambios/i });
    await user.click(submitBtn);

    expect(await screen.findByText(/formato de teléfono inválido/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('submits trimmed personal data when input is valid', async () => {
    const user = userEvent.setup();
    render(
      <PersonalDataForm
        initialData={sampleData}
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
      />
    );

    const firstNameInput = screen.getByLabelText(/nombre/i);
    await user.clear(firstNameInput);
    await user.type(firstNameInput, '  Agustin  ');

    const lastNameInput = screen.getByLabelText(/apellido/i);
    await user.clear(lastNameInput);
    await user.type(lastNameInput, '  Fraga  ');

    const submitBtn = screen.getByRole('button', { name: /guardar cambios/i });
    await user.click(submitBtn);

    expect(mockOnSubmit).toHaveBeenCalledWith({
      firstName: 'Agustin',
      lastName: 'Fraga',
      phone: '+54 9 11 2345-6789',
    });
  });

  it('resets inputs to initial data and calls onCancel on discard changes click', async () => {
    const user = userEvent.setup();
    render(
      <PersonalDataForm
        initialData={sampleData}
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
      />
    );

    const firstNameInput = screen.getByLabelText(/nombre/i);
    await user.clear(firstNameInput);
    await user.type(firstNameInput, 'OtroNombre');

    const cancelBtn = screen.getByRole('button', { name: /cancelar|descartar/i });
    await user.click(cancelBtn);

    expect(firstNameInput).toHaveValue('Valentino');
    expect(mockOnCancel).toHaveBeenCalled();
  });
});
