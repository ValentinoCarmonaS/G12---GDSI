import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StaffFormModal } from '@/components/staff/StaffFormModal';
import { StaffMemberResponse } from '@/types/staff';

describe('StaffFormModal Component (FR-001, FR-002, FR-005, FR-026)', () => {
  const mockOnSubmit = vi.fn();
  const mockOnClose = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not render modal when isOpen is false', () => {
    render(
      <StaffFormModal
        isOpen={false}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    expect(screen.queryByText(/registrar nuevo empleado/i)).not.toBeInTheDocument();
  });

  it('renders creation form inputs when initialData is not provided', () => {
    render(
      <StaffFormModal
        isOpen={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    expect(screen.getByRole('heading', { name: /registrar nuevo empleado/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/nombre completo/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/correo electrónico/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/rol/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /registrar empleado/i })).toBeInTheDocument();
  });

  it('populates fields with initialData when in edit mode', () => {
    const existingStaff: StaffMemberResponse = {
      id: 'staff-101',
      fullName: 'Carlos Chef',
      email: 'carlos@restaurant.com',
      role: 'CHEF',
      status: 'ACTIVE',
      createdAt: '2026-10-24T10:00:00Z',
      updatedAt: '2026-10-24T10:00:00Z',
    };

    render(
      <StaffFormModal
        isOpen={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
        initialData={existingStaff}
      />
    );

    expect(screen.getByRole('heading', { name: /editar miembro del personal/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/nombre completo/i)).toHaveValue('Carlos Chef');
    expect(screen.getByLabelText(/correo electrónico/i)).toHaveValue('carlos@restaurant.com');
    expect(screen.getByLabelText(/rol/i)).toHaveValue('CHEF');
    expect(screen.getByRole('button', { name: /guardar cambios/i })).toBeInTheDocument();
  });

  it('displays validation errors on empty fields', async () => {
    const user = userEvent.setup();
    render(
      <StaffFormModal
        isOpen={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    await user.click(screen.getByRole('button', { name: /registrar empleado/i }));

    expect(await screen.findByText(/el nombre completo es obligatorio/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('displays validation error on invalid email format', async () => {
    const user = userEvent.setup();
    render(
      <StaffFormModal
        isOpen={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    await user.type(screen.getByLabelText(/nombre completo/i), 'Laura Encargada');
    await user.type(screen.getByLabelText(/correo electrónico/i), 'invalid-email-format');
    await user.click(screen.getByRole('button', { name: /registrar empleado/i }));

    expect(await screen.findByText(/formato de email inválido/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('normalizes email to lowercase and submits valid creation payload', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockResolvedValueOnce(undefined);

    render(
      <StaffFormModal
        isOpen={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    await user.type(screen.getByLabelText(/nombre completo/i), 'Laura Encargada');
    await user.type(screen.getByLabelText(/correo electrónico/i), 'Laura.Encargada@Restaurant.COM');
    await user.selectOptions(screen.getByLabelText(/rol/i), 'ENCARGADO');

    await user.click(screen.getByRole('button', { name: /registrar empleado/i }));

    expect(mockOnSubmit).toHaveBeenCalledWith({
      fullName: 'Laura Encargada',
      email: 'laura.encargada@restaurant.com',
      role: 'ENCARGADO',
    });
  });

  it('displays submission error message when backend rejects request (e.g. 409 conflict)', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockRejectedValueOnce({
      response: {
        status: 409,
        data: { detail: 'El email ya se encuentra registrado en el sistema.' },
      },
    });

    render(
      <StaffFormModal
        isOpen={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    await user.type(screen.getByLabelText(/nombre completo/i), 'Juan Admin');
    await user.type(screen.getByLabelText(/correo electrónico/i), 'juan@restaurant.com');
    await user.selectOptions(screen.getByLabelText(/rol/i), 'ADMINISTRADOR');

    await user.click(screen.getByRole('button', { name: /registrar empleado/i }));

    expect(
      await screen.findByText(/el email ya se encuentra registrado en el sistema/i)
    ).toBeInTheDocument();
  });
});
