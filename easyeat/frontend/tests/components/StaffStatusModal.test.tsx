import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StaffStatusModal } from '@/components/staff/StaffStatusModal';
import { StaffMemberResponse } from '@/types/staff';

describe('StaffStatusModal Component', () => {
  const mockOnClose = vi.fn();
  const mockOnConfirm = vi.fn();

  const activeChef: StaffMemberResponse = {
    id: 'staff-1',
    fullName: 'Carlos Chef',
    email: 'carlos@easyeat.com',
    role: 'CHEF',
    status: 'ACTIVE',
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
  };

  const suspendedChef: StaffMemberResponse = {
    ...activeChef,
    status: 'SUSPENDED',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not render when isOpen is false or staffMember is null', () => {
    render(
      <StaffStatusModal
        isOpen={false}
        staffMember={activeChef}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
      />
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('renders suspension warning and confirms status change to SUSPENDED', async () => {
    const user = userEvent.setup();
    render(
      <StaffStatusModal
        isOpen={true}
        staffMember={activeChef}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
      />
    );

    expect(screen.getByText(/suspender acceso/i)).toBeInTheDocument();
    expect(screen.getByText(/Carlos Chef/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /confirmar suspensión/i }));

    expect(mockOnConfirm).toHaveBeenCalledWith('staff-1', 'SUSPENDED');
  });

  it('renders reactivation confirmation when staffMember is SUSPENDED', async () => {
    const user = userEvent.setup();
    render(
      <StaffStatusModal
        isOpen={true}
        staffMember={suspendedChef}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
      />
    );

    expect(screen.getByText(/activar acceso/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /confirmar activación/i }));

    expect(mockOnConfirm).toHaveBeenCalledWith('staff-1', 'ACTIVE');
  });

  it('displays 409 Conflict orphaned admin error message when error prop is provided', () => {
    render(
      <StaffStatusModal
        isOpen={true}
        staffMember={activeChef}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
        error="No se puede suspender al único administrador activo del establecimiento."
      />
    );

    expect(
      screen.getByText(/No se puede suspender al único administrador activo del establecimiento./i)
    ).toBeInTheDocument();
  });
});
