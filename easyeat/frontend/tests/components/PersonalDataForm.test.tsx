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

  describe('Demographics fields and validation (T010, COM-1.4.2)', () => {
    const demographicsSample = {
      ...sampleData,
      age: 28,
      gender: 'Femenino',
      address: 'Av. Santa Fe 1234',
    };

    it('renders demographic fields (age, gender, address) with initial values', () => {
      render(
        <PersonalDataForm
          initialData={demographicsSample}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      expect(screen.getByLabelText(/edad/i)).toHaveValue(28);
      expect(screen.getByLabelText(/género/i)).toHaveValue('Femenino');
      expect(screen.getByLabelText(/domicilio/i)).toHaveValue('Av. Santa Fe 1234');
    });

    it('displays validation error when age is below 14 or above 120', async () => {
      const user = userEvent.setup();
      render(
        <PersonalDataForm
          initialData={demographicsSample}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      const ageInput = screen.getByLabelText(/edad/i);
      await user.clear(ageInput);
      await user.type(ageInput, '13');

      const submitBtn = screen.getByRole('button', { name: /guardar cambios/i });
      await user.click(submitBtn);

      expect(await screen.findByText(/la edad debe ser un número entero entre 14 y 120/i)).toBeInTheDocument();
      expect(mockOnSubmit).not.toHaveBeenCalled();

      await user.clear(ageInput);
      await user.type(ageInput, '121');
      await user.click(submitBtn);

      expect(await screen.findByText(/la edad debe ser un número entero entre 14 y 120/i)).toBeInTheDocument();
      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('displays validation error when address exceeds 255 characters', async () => {
      const user = userEvent.setup();
      render(
        <PersonalDataForm
          initialData={demographicsSample}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      const addressInput = screen.getByLabelText(/domicilio/i);
      await user.clear(addressInput);
      await user.type(addressInput, 'A'.repeat(256));

      const submitBtn = screen.getByRole('button', { name: /guardar cambios/i });
      await user.click(submitBtn);

      expect(await screen.findByText(/el domicilio no puede exceder los 255 caracteres/i)).toBeInTheDocument();
      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('submits demographic data when input is valid', async () => {
      const user = userEvent.setup();
      render(
        <PersonalDataForm
          initialData={demographicsSample}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      const ageInput = screen.getByLabelText(/edad/i);
      await user.clear(ageInput);
      await user.type(ageInput, '32');

      const genderSelect = screen.getByLabelText(/género/i);
      await user.selectOptions(genderSelect, 'No binario');

      const addressInput = screen.getByLabelText(/domicilio/i);
      await user.clear(addressInput);
      await user.type(addressInput, 'Av. Corrientes 555');

      const submitBtn = screen.getByRole('button', { name: /guardar cambios/i });
      await user.click(submitBtn);

      expect(mockOnSubmit).toHaveBeenCalledWith(expect.objectContaining({
        firstName: 'Valentino',
        lastName: 'Carmona',
        age: 32,
        gender: 'No binario',
        address: 'Av. Corrientes 555',
      }));
    });
  });
});
