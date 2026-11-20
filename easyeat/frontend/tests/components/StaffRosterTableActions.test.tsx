import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StaffRosterTable } from '@/components/staff/StaffRosterTable';
import { StaffMemberResponse } from '@/types/staff';

describe('StaffRosterTable Actions', () => {
  const mockOnEdit = vi.fn();
  const mockOnToggleStatus = vi.fn();

  const staffMembers: StaffMemberResponse[] = [
    {
      id: 'staff-1',
      fullName: 'Carlos Chef',
      email: 'carlos@easyeat.com',
      role: 'CHEF',
      status: 'ACTIVE',
      createdAt: '2026-10-01T10:00:00Z',
      updatedAt: '2026-10-01T10:00:00Z',
    },
    {
      id: 'staff-2',
      fullName: 'Lucia Encargada',
      email: 'lucia@easyeat.com',
      role: 'ENCARGADO',
      status: 'SUSPENDED',
      createdAt: '2026-10-01T10:00:00Z',
      updatedAt: '2026-10-01T10:00:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders Suspender button for ACTIVE member and Activar button for SUSPENDED member', () => {
    render(
      <StaffRosterTable
        staffMembers={staffMembers}
        onEdit={mockOnEdit}
        onToggleStatus={mockOnToggleStatus}
      />
    );

    expect(screen.getByRole('button', { name: /suspender/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /activar/i })).toBeInTheDocument();
  });

  it('calls onToggleStatus with the selected staff member when action button is clicked', async () => {
    const user = userEvent.setup();
    render(
      <StaffRosterTable
        staffMembers={staffMembers}
        onEdit={mockOnEdit}
        onToggleStatus={mockOnToggleStatus}
      />
    );

    await user.click(screen.getByRole('button', { name: /suspender/i }));
    expect(mockOnToggleStatus).toHaveBeenCalledWith(staffMembers[0]);

    await user.click(screen.getByRole('button', { name: /activar/i }));
    expect(mockOnToggleStatus).toHaveBeenCalledWith(staffMembers[1]);
  });
});
